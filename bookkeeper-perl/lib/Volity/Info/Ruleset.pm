package Volity::Info::Ruleset;

use warnings; use strict;

use base qw(Volity::Info);
use Volity::Info::Game;

__PACKAGE__->table('ruleset');
__PACKAGE__->columns(All=>qw(id uri name description player_id homepage));
__PACKAGE__->has_a(player_id=>"Volity::Info::Player");
__PACKAGE__->has_many(servers=>"Volity::Info::Server", "ruleset_id");

##########################
# Ima::DBI class methods
##########################

# search_with_player: returns the rulesets associated with the given player.
Volity::Info::Ruleset->set_sql(with_player => "select distinct ruleset.id from ruleset, game_seat, player_seat, game where game.id = game_seat.game_id and game_seat.seat_id = player_seat.seat_id and player_seat.player_id = ? and game.ruleset_id = ruleset.id");

# search_with_seat: returns the rulesets associated with the given seat.
Volity::Info::Ruleset->set_sql(with_seat => "select distinct ruleset.id from ruleset, game_seat, game where game.id = game_seat.game_id and game_seat.seat_id = ? and game.ruleset_id = ruleset.id");

# search_with_seat_by_rating: as above, but ordered by the seat's rating.
#Volity::Info::Ruleset->set_sql(with_seat_by_rating => "select distinct ruleset.id, max(game_seat.rating) as rating from ruleset, game_seat, game where game.id = game_seat.game_id and game_seat.seat_id = ? and game.ruleset_id = ruleset.id group by ruleset.id order by rating desc");
Volity::Info::Ruleset->set_sql(with_seat_by_rating => qq {
    SELECT ruleset.id,
      (SELECT rating 
       FROM game_seat 
       INNER JOIN game ON game.id=game_seat.game_id 
       WHERE game_seat.seat_id=? AND ruleset_id=ruleset.id 
       ORDER BY game.end_time 
       DESC LIMIT 0,1
       ) 
    AS rating FROM ruleset GROUP BY id HAVING rating > 0 ORDER BY rating DESC
});

# search_with_seat_by_number_of_plays: as above, but ordered by the 
# number of times the seat has played the game.
Volity::Info::Ruleset->set_sql(with_seat_by_number_of_plays => "select distinct ruleset.id, count(game.id) as number_of_plays from ruleset, game_seat, game where game.id = game_seat.game_id and game_seat.seat_id = ? and game.ruleset_id = ruleset.id group by ruleset.id order by number_of_plays desc");

# number of times this ruleset has been played at all.
Volity::Info::Ruleset->set_sql(number_of_games_played=>"select count(*) from game where ruleset_id = ?");

# number_of_games_played: returns the number of times that this ruleset has
# been played at all.
sub number_of_games_played {
  my $self = shift;
  my $sth = $self->sql_number_of_games_played;
  $sth->execute($self->id);
  my ($number) = $sth->fetch;
  $sth->finish;
  return $number;
}


1;
