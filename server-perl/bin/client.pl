use strict;
#use warnings;
#         sub POE::Kernel::ASSERT_DEFAULT () { 1 }
#         sub POE::Kernel::TRACE_DEFAULT  () { 1 }

use POE;
use POE::Component::Jabber;
use POE::Wheel::ReadLine;
use PXR::Node;
use PXR::NS qw/ :JABBER :IQ /;
use XMLRPC::Lite;
use YAML;
our @cmd_hist = eval { @{ YAML::LoadFile("client.yaml") } };
our %query_ids;
our $serializer = XMLRPC::Serializer->new;
our $deserializer = XMLRPC::Deserializer->new;

$serializer->encoding( undef ); # get rid fo the <?xml> header

POE::Session->create(
    inline_states => {
        _start => \&start,
        server_init   => \&server_init,
        server_input  => \&server_input,
        server_auth   => \&server_auth,
        user_input => \&user_input,
        });

POE::Kernel->run();

#null_game, foo

sub start {
    my($session,$heap) = @_[SESSION,HEAP];
    print "started...\n";
    $heap->{'user'} = POE::Wheel::ReadLine->new(
        InputEvent => 'user_input',
        PutMode => 'immediate',
        );
   foreach (@cmd_hist) {
       $heap->{'user'}->addhistory($_);
   }
   $heap->{'user'}->get( 'Volity> ' );
   $heap->{'user'}->put("newed");
}

sub server_init {
    my($kernel,$heap) = @_[KERNEL,HEAP];
    $heap->{'user'}->put("Connected.");
}

sub server_input {
    my($heap,$node) = @_[HEAP,ARG0..$#_];
    my $element_type = $node->name;
    my $subname = "jabber_$element_type";
    if (my $sub = __PACKAGE__->can($subname)) {
        &$sub(@_[0..(ARG0-1)],$node);
    } else {
        $heap->{'user'}->put("UNSUPPORTED ELEMENT: $element_type".$node->to_str);
    }
}

sub server_auth {
    my($heap,$node)= @_[HEAP,ARG0..$#_];
    if ($node->name eq 'handshake') {
        $heap->{'user'}->put( "AUTHED HANDSHAKE: ".$node->to_str );
    } else {
        $heap->{'user'}->put( "AUTHED UNKNOWN: ".$node->to_str );
    }
}

sub jabber_message {
    my($heap,$node) = @_[HEAP,ARG0..$#_];
    $heap->{'last'} = $node->attr('from');
    $heap->{'lasttype'} = $node->attr('type');
    $heap->{'user'}->put( $node->attr('from').": ".$node->get_tag('body')->data );
}

sub jabber_iq {
    my($heap,$node) = @_[HEAP,ARG0..$#_];
    my $id = $node->attr('id');
    my $from_jid = $node->attr('from');
    my $query;
    if ($node->attr('type') eq 'result') {
        if ($query = $node->get_tag('query') and 
            $query->attr('xmlns') eq 'jabber:iq:rpc' and
            my $sub = __PACKAGE__->can("rpc_$id")) {

            unless ($query_ids{$id}) {
                $heap->{'user'}->put( "IQ RESULT NO ID: ".$node->to_str );
                return;
            }
            $query_ids{$id}--;

            my $raw_xml = join("\n", map($_->to_str, @{$query->get_children}));
            my $response = $deserializer->deserialize($raw_xml);
            &$sub(@_[0..(ARG0-1)],$response->result);
        }  else {
            $heap->{'user'}->put( "IQ RESULT: ".$node->to_str );
        }
    } else {
      $heap->{'user'}->put( "IQ: ".$node->to_str );
    }
}

sub cmd_help {
    my($heap,$on) = @_[HEAP,ARG0..$#_];
    $heap->{'user'}->put("$_") for
        'Volity client commands:',
        '  help',
        '  connect',
        '  login',
        '  nick',
        '  rpc',
        '  info',
        '  presence',
        '  new_game',
        '  join_game',
        '  start_game',
        '  msg',
        '  gmsg',
        '  game',
        '  ref',
        '  quit';
}

sub cmd_connect {
    my($heap,$session,$host,$port) = @_[HEAP,SESSION,ARG0..$#_];
    unless ($host) {
        $heap->{'user'}->put("Form: connect <host> [<port>]");
        return;
    }
    $port ||= 5222;
    $heap->{'host'} = $host;
    $heap->{'port'} = $port;
    POE::Component::Jabber->new(
      PORT => $port,
      HOSTNAME => $host,
      ALIAS => 'server',
      STATE_PARENT => $session->ID,
#      DEBUG=>1,
      STATES => {
        INITFINISH => 'server_init',
        INPUTEVENT => 'server_input',
      }
    );
}

sub cmd_login {
    my($kernel,$heap,$username,$password) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($username and $password) {
        $heap->{'user'}->put("Form: login <username> <password>");
        return;
    }
    $heap->{'user'}->put("Logging in as $username");
    my $resource = 'VolityClient';
    $kernel->post(server => 'set_auth', 'server_auth',$username,$password,$resource);
    $heap->{'username'} = $username;
    $heap->{'resource'} = $resource;
    $heap->{'nick'} = $username;
}

sub cmd_nick {
    my($heap,$nickname) = @_[HEAP,ARG0..$#_];
    unless ($nickname) {
        $heap->{'user'}->put("Form: nick <nickname>");
        return;
    }
    $heap->{'nick'} = $nickname;
    $heap->{'user'}->put("Nickname set.");
}

sub cmd_info {
    my($heap) = $_[HEAP];
    my $jid = $heap->{'username'}.'@'.$heap->{'host'};
    $jid .= ':'.$heap->{'port'} if $heap->{'port'} != 5222;
    $jid .= "/".$heap->{'resource'};
    $heap->{'user'}->put($_) for
      "Status information:",
      "  JID: ".$jid,
      "  Nickname: ".$heap->{'nick'},
      "  Game JID: ".$heap->{'game'},
      "  Referree JID: ".$heap->{'ref'};
}

sub send_rpc_query {
    my($kernel,$id,$to,$cmd,@args) = @_;
    my $iq = PXR::Node->new('iq');
    $iq->attr( to   => $to );
    $iq->attr( id   => $id );
    $iq->attr( type => 'set' );
    $iq->insert_tag( 'query', 'jabber:iq:rpc' ) ->
        rawdata( $serializer->envelope( method=> $cmd, @args ) );
    $kernel->post( server => 'output_handler', $iq );
    $query_ids{$id} ++;
    return $iq;
}

sub cmd_rpc {
    my($kernel,$heap,$id,$to,$cmd,@args) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($id and $to and $cmd) {
        $heap->{'user'}->put("Form: rpc <id> <to> <cmd>");
        return;
    }
    my $iq = send_rpc_query($kernel,$id,$to,$cmd,@args);
    $heap->{'user'}->put( $iq->to_str );
}

sub cmd_new_game {
    my($kernel,$heap,$to) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($to) {
        $heap->{'user'}->put("Form: new_game <to>");
        return;
    }
    send_rpc_query($kernel,'new_game',$to,'new_game');
    $heap->{'user'}->put( "Request for a new game submitted" );
}

sub join_muc {
    my($kernel,$game_jid,$nick) = @_;
    my $muc_jid = "$game_jid/$nick";
    my $presence = PXR::Node->new('presence');
    $presence->attr(to=>$muc_jid);
    $presence->insert_tag('x', 'http://jabber.org/protocol/muc');
    $kernel->post( server => 'output_handler', $presence );
}

sub rpc_new_game {
    my($kernel,$heap,$game_jid) = @_[KERNEL,HEAP,ARG0..$#_];
    $heap->{'game'} = $game_jid;
    $heap->{'ref'}  = "$game_jid/volity";
    join_muc($kernel,$game_jid,$heap->{'nick'});
    $heap->{'user'}->put( "Game created with $game_jid" );
}

sub cmd_join_game {
    my($kernel,$heap,$game_jid) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($game_jid) {
        $heap->{'user'}->put("Form: join_game <jid>");
        return;
    }
    $heap->{'game'} = $game_jid;
    $heap->{'ref'}  = "$game_jid/volity";
    join_muc($kernel,$game_jid,$heap->{'nick'});
    $heap->{'user'}->put( "Admintance requested to game" );
}

sub cmd_start_game {
    my($kernel,$heap) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($heap->{'ref'}) {
        $heap->{'user'}->put("Form: start_game");
        $heap->{'user'}->put("  You must have joined a game with new_game.");
        return;
    }
    send_rpc_query($kernel,'start_game',$heap->{'ref'},'start_game');
    $heap->{'user'}->put( "Request to start the game submitted" );
}
sub rpc_start_game {
    my($kernel,$heap) = @_[KERNEL,HEAP];
    $heap->{'user'}->put( "The game has begun, make your time!" );
}

sub send_message {
    my($kernel,$config) = @_;
    my $message = PXR::Node->new('message');
    foreach (qw(to type from)) {
        $message->attr($_=>$$config{$_}) if defined($$config{$_});
    }
    foreach (qw(thread)) {
        $message->insert_tag($_)->data($$config{$_}) if defined($$config{$_});
    }
    foreach (qw(subject body)) {
        if (defined($$config{$_})) {
            if (ref($$config{$_}) and ref($$config{$_}) eq 'HASH') {
                while (my($language, $text) = each(%{$$config{$_}})) {
                    unless ($language =~ /^\w\w$|^\w\w-\w\w$/) {
                        croak("Language must be of the form 'xx' or 'xx-xx', but you sent '$language'.");
                    }
                    my $tag = $message->insert_tag($_);
                    $tag->attr("xml:lang"=>$language);
                    $tag->data($text);
                }
            } elsif (not(ref($$config{$_}))) {
                $message->insert_tag($_)->data($$config{$_});
            } else {
                croak("$_ must be either a hashref (for localization) or a string (for default langauge)");
            }
        }
    }
    $kernel->post( server => 'output_handler', $message );
}

sub cmd_msg {
    my($kernel,$heap,$to,$message) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($message) {
        $heap->{'user'}->put("Form: msg <to> <message>");
        return;
    }
    send_message($kernel,{to=>$to,type=>"chat",body=>$message});
    $heap->{'user'}->put( "Chat message sent" );
}

sub cmd_reply {
    my($kernel,$heap,$message) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($message and $heap->{'last'}) {
        $heap->{'user'}->put("Form: reply <message>");
        $heap->{'user'}->put("  You must have received a message in order to reply.");
        return;
    }
    send_message($kernel,{to=>$heap->{'last'},type=>$heap->{'lasttype'},body=>$message});
    $heap->{'user'}->put( "Chat message sent" );
}


sub cmd_gmsg {
    my($kernel,$heap,$to,$message) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($message) {
        $heap->{'user'}->put("Form: gmsg <to> <message>");
        return;
    }
    send_message($kernel,{to=>$to,type=>"groupchat",body=>$message});
    $heap->{'user'}->put( "Groupchat message sent" );
}

sub cmd_game {
    my($kernel,$heap,$message) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($message and $heap->{'game'}) {
        $heap->{'user'}->put("Form: game <message>");
        $heap->{'user'}->put("  Send a message to the game MUC -- must have joined one with new_game");
        return;
    }
    send_message($kernel,{to=>$heap->{'game'},type=>"groupchat",body=>$message});
    $heap->{'user'}->put( "Game message sent" );
}

sub cmd_ref {
    my($kernel,$heap,$message) = @_[KERNEL,HEAP,ARG0..$#_];
    unless ($message and $heap->{'ref'}) {
        $heap->{'user'}->put("Form: ref <message>");
        $heap->{'user'}->put("  Send a message to the game referree.");
        $heap->{'user'}->put("  must have joined a game with new_game");
        return;
    }
    send_message($kernel,{to=>$heap->{'ref'},type=>"chat",body=>$message});
    $heap->{'user'}->put( "Referree message sent" );
}

sub cmd_presence {
    my($kernel,$heap,$type,$show,$status,$to) = @_[KERNEL,HEAP,ARG0..$#_];
    if ($type) {
        $kernel->post('POCO', 'send_presence', $type, $show, $status, $to);
        $heap->{'user'}->put( "Presence sent to $to" );
    } else {
        $kernel->post('POCO', 'send_presence');
        $heap->{'user'}->put( "Presence sent to roster" );
    }
}
                                    
sub cmd_quit {
    my($heap) = $_[HEAP];
    $heap->{'user'}->put('Quiting.');
    exit;
}

END {
    YAML::DumpFile("client.yaml",\@cmd_hist);
}

sub user_input {
    my($heap,$input,$exception) = @_[HEAP, ARG0, ARG1];
    if (defined $input) {
        $heap->{'user'}->get('Volity> ');
        $heap->{'user'}->addhistory($input);
        push(@cmd_hist,$input);
        my($cmd,@args) = tokenize($input,{});
        if (my $sub = __PACKAGE__->can("cmd_$cmd")) {
            &$sub(@_[0..(ARG0-1)],@args);
        } elsif ($cmd eq '') {
            # Do nothing.
        } else {
            $heap->{'user'}->put("Unknown command: $input");
        }
    } else {
        $heap->{'user'}->put("Exception: $exception");
        exit;
    }
}

sub tokenize {
    my($str,$vars) = @_;
    my @tokens;
    my $tok_count = 0;
    my $last = 0;
    while ($str =~ /\G(?:"([^"]+)(?<!\\)"|'([^']+)(?<!\\)'|((?:[^\s"']|(?<=\\)'|(?<=\\)"|(?<=\\)\s)+)|((?:(?<!\\)\s)+))/g) {
        $last = pos($str);
        my($dq,$sq,$nq,$sp) = ($1,$2,$3,$4);
        my $val = $dq||$sq||$nq||$sp;
        if ($dq or $nq) {
            $val = expand($val,$vars);
        } else {
            $val = unescape($val);
        }
        if ($sp) {
            $tok_count ++ if $#tokens == $tok_count;
        } else {
            $tokens[$tok_count] .= $val;
        }
    }
    if ($last < length($str)) {
        $tokens[$tok_count] .= substr($str,$last);
    }
    return @tokens;
}

sub unescape {
    my($str) = @_;
    $str =~ s/\\(.)/$1/g;
    return $str;
}

sub expand {
    my($str,$vars) = @_;
    $str =~ s{
            (?:
            (?<!\\)\$(\w+) |
            \\(.)
            )
        }{
            if ($1) {
                $$vars{$1};
            } else {
                $2
            }
        }gxe;
    $str = unescape($str);
    return $str;
}

