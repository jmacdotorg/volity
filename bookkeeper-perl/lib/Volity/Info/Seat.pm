package Volity::Info::Seat;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::Seat->table('seat');
Volity::Info::Seat->columns(All=>qw(id));
Volity::Info::Seat->has_many(players=>["Volity::Info::PlayerSeat" => 'player_id'], 'seat_id');
Volity::Info::Seat->has_many(games=>["Volity::Info::GameSeat" => 'game_id'], 'seat_id');

# Deep voodoo here. When I want to search with exact players, I want to _not_
# match any seat that has these players as well as others. I want the seat with
# _only_ the given players. There should be one or zero of them.
sub search_with_exact_players {
    my $self = shift;
    my (@players) = @_;
    my @bind_values;
    return () unless @players;
    my $not_where = join(' AND ', map("player_id != ?", @players));
    @bind_values = map($_->id, @players);
    my $statement = "select id from seat where not exists (select player_seat.id from player_seat where seat_id = seat.id and $not_where) ";
    foreach (@players) {
	push (@bind_values, $_->id);
	$statement .= " and exists (select player_seat.id from player_seat where seat_id = seat.id and player_id = ?) ";
    }

    my $sth = Volity::Info::Seat->db_Main->prepare($statement);
    $sth->execute(@bind_values);
    my @seats;
    while (my ($seat_id) = $sth->fetchrow_array) { 
	push (@seats, Volity::Info::Seat->retrieve($seat_id));
    }
    return @seats;
}

# Set some Ima::DBI SQL statements.
Volity::Info::Seat->set_sql(current_rating_by_uri=>qq{select rating from game_seat, game, ruleset where game.id = game_seat.game_id and ruleset.id = game.ruleset_id and game_seat.seat_id = ? and ruleset.uri = ? order by game.end_time desc limit 0,1});

Volity::Info::Seat->set_sql(current_rating_by_ruleset=>qq{select rating from game_seat, game where game.id = game_seat.game_id  and game_seat.seat_id = ? and game.ruleset_id = ? order by game.end_time desc});

Volity::Info::Seat->set_sql(current_rounded_rating_by_uri=>qq{select round(rating,0) from game_seat, game, ruleset where game.id = game_seat.game_id and ruleset.id = game.ruleset_id and game_seat.seat_id = ? and ruleset.uri = ? order by game.end_time desc limit 0,1});

Volity::Info::Seat->set_sql(current_rounded_rating_by_ruleset=>qq{select round(rating,0) from game_seat, game where game.id = game_seat.game_id  and game_seat.seat_id = ? and game.ruleset_id = ? order by game.end_time desc});

Volity::Info::Seat->set_sql(number_of_games_played_by_ruleset=>qq{select count(game_seat.game_id) from game_seat, game where game.id = game_seat.game_id and game_seat.seat_id = ? and game.ruleset_id = ?});

Volity::Info::Seat->set_sql(number_of_games_played=>qq{select count(game_seat.game_id) from game_seat, game where game.id = game_seat.game_id and game_seat.seat_id = ?});

Volity::Info::Seat->set_sql(number_of_wins_for_ruleset=>qq{select count(game_seat.game_id) from game_seat, game where game.id = game_seat.game_id and place = 1 and game_seat.seat_id = ? and game.ruleset_id = ?});

Volity::Info::Seat->set_sql(with_player_and_ruleset=>qq{select distinct seat.id from game, seat, game_seat, player_seat where seat.id = game_seat.seat_id and game.id = game_seat.game_id and player_seat.seat_id = game_seat.seat_id and player_seat.player_id = ? and ruleset_id = ?});

Volity::Info::Seat->set_sql(with_ruleset_by_rating =>qq{
SELECT seat.id, 
(
   SELECT rating
   FROM game_seat
   INNER JOIN game AS g ON game_seat.game_id=g.id
   WHERE seat_id=seat.id AND g.ruleset_id = game.ruleset_id
   ORDER BY end_time DESC
   LIMIT 0,1
) AS rating
FROM game
INNER JOIN game_seat ON game.id = game_seat.game_id
INNER JOIN seat ON game_seat.seat_id = seat.id
WHERE game.ruleset_id = ?
GROUP BY seat.id
ORDER BY rating DESC
LIMIT ?,?
});

# current_rating_for_uri: Return the seat's current ranking for the given
# ruleset URI. Defaults to 1500, if the seat has no ranking.
sub current_rating_for_uri {
  my $self = shift;
  my ($uri) = @_;
  my $sth = $self->sql_current_rating_by_uri;
  $sth->execute($self->id, $uri);
  my ($rating) = $sth->fetch;
  $rating ||= 1500;
  $sth->finish;
  return $rating;
}

# current_rating_for_ruleset: as above, but takes a Volity::Info::Ruleset
# object instead.
sub current_rating_for_ruleset {
  my $self = shift;
  my ($ruleset) = @_;
  my @caller = caller;
  my $sth = $self->sql_current_rating_by_ruleset;
  $sth->execute($self->id, $ruleset->id);
  my ($rating) = $sth->fetch;
  # This second attempt with eval is a hack, but I dunno how to work around it.
  # I kept getting undef where I shouldn't have... bizzare. Oh well.
  ($rating) = eval { $sth->fetch } unless defined($rating);
  $rating ||= 1500;
  $sth->finish;
  return $rating;
}

sub number_of_games_played {
    my $self = shift;
  my $sth = $self->sql_number_of_games_played;
  $sth->execute($self->id);
  my ($number) = $sth->fetch;
  $sth->finish;
  return $number;
}
    

sub number_of_games_played_for_ruleset {
  my $self = shift;
  my ($ruleset) = @_;
  my $sth = $self->sql_number_of_games_played_by_ruleset;
  $sth->execute($self->id, $ruleset->id);
  my ($number) = $sth->fetch;
  $sth->finish;
  return $number;
}

sub number_of_wins_for_ruleset {
    my $self = shift;
    my ($ruleset) = @_;
    my $sth = $self->sql_number_of_wins_for_ruleset;
    $sth->execute($self->id, $ruleset->id);
    my ($number) = $sth->fetch;
    $sth->finish;
    return $number;
}



# rulsets: Return Volity::Info::Ruleset objects corresponding to rulesets
# that this seat has played.
sub rulesets {
    my $self = shift;
    my @rulesets = Volity::Info::Ruleset->search_with_seat($self);
    return @rulesets;
}

1;
