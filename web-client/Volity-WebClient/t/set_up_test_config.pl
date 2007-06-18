# This is invoked by Makefile.PL.

use warnings;
use strict;

use YAML;

use Readonly;
use English;
use Carp qw(carp croak);

Readonly my $CONFIG_FILE => 't/test_config.yml';

if (check_old_config()) {

    print "In order to properly test this, I need some \n";
    print "valid Jabber login credentials.\n";
    print "Do you want to just skip these network tests? [n] ";
    chomp(my $answer = <STDIN>);
    
    if ($answer =~ /^y/i) {
#        unlink $CONFIG_FILE;
    }
    else {
        perform_setup();
    }
}

sub check_old_config {
    if (-e $CONFIG_FILE) {
        my $old_config_ref;
        eval { $old_config_ref = YAML::LoadFile($CONFIG_FILE) };
        if ($EVAL_ERROR) {
            carp("Old config file found at $CONFIG_FILE but I can't parse it. Ignoring.\n");
            return 1;
        }
        print q{I see an old test config file, using the Jabber username '}
          . $old_config_ref->{jabber_username}
          . qq{'.\n};
        print 'Do you want to re-use this config for testing? [y] ';
        chomp(my $answer = <STDIN>);
        if ( (not($answer)) or ($answer =~ /^y/i) ) {
#            write_config_file($old_config_ref);
            return 0;
        }
        print "OK, let's make a new config file, then.\n";
        return 1;
    }
    else {
        return 1;
    }
}

sub perform_setup {
    my ($jid, $username, $password, $host, $resource);
    until (defined($jid)) {
        print 'Please give me a valid Jabber JID (i.e. foo@bar.com): ';
        chomp($jid = <STDIN>);
        ($username, $host, $resource) = $jid
            =~ m{^(.*?)@(.*?)(?:/(.*))?$};
            if ($username && not($resource)) {
                # Ask for resource. Eh, maybe later.
            } elsif (not($username)) {
                print "That doesn't look like a valid JID. Let's try again.\n";
                undef($jid);
                next;
            }
    }
    until (defined($password)) {
        print 'Please give me the password for that JID: ';
        chomp($password = <STDIN>);
    }
    my %config = (
                  jabber_username => $username,
                  jabber_password => $password,
                  jabber_host     => $host,
                  jabber_resource => 'volitywebclientperltest1',
                  test_resource   => 'volitywebclientperltest2',
              );
    write_config_file(\%config);
}
   

sub write_config_file {
    my ($config_ref) = @_;
    eval { YAML::DumpFile($CONFIG_FILE, $config_ref) };
    if ($EVAL_ERROR) {
        croak("Uh oh, failed to dump the config hash to $CONFIG_FILE:\n$EVAL_ERROR\n");
    }
}
