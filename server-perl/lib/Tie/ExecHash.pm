package Tie::ExecHash;
use Tie::Hash;
use base 'Tie::ExtraHash';

sub STORE {
    if ( ref $_[2] eq 'ARRAY'   and
         @{$_[2]} == 2          and
         ref $_[2][0] eq 'CODE' and
         ref $_[2][1] eq 'CODE') {
        $_[0][1]{'set'}{$_[1]} = $_[2][0];
        $_[0][1]{'get'}{$_[1]} = $_[2][1];
        $_[0]->SUPER::STORE($_[1],'');
    } elsif ( exists $_[0][1]{'set'}{$_[1]} ) {
        return &{ $_[0][1]{'set'}{$_[1]} }( $_[2] );
    } else {
        goto $_[0]->can('SUPER::STORE');
    }
}

sub FETCH {
    if ( exists $_[0][1]{'get'}{$_[1]} ) {
        return &{ $_[0][1]{'get'}{$_[1]} }();
    } else {
        goto $_[0]->can('SUPER::FETCH');
    }
}

sub DELETE {
    if ( exists $_[0][1]{'set'}{$_[1]} ) {
        $_[0]->SUPER::DELETE($_[1]);
        return &{ $_[0][1]{'set'}{$_[1]} }();
    } else {
        goto $_[0]->can('SUPER::DELETE');
    }
}

1;
__END__
=pod

=head1 NAME

Tie::ExecHash - Give special powers only to some keys

=head1 SYNOPSIS

use Tie::ExecHash;

my %foo = ();
tie( %foo, 'Tie::ExecHash');

$foo{'bar'} = 1;
print "$foo{'bar'}\n"; # 1

my $baz = "red";

$foo{'bar'} = [ sub { $baz = $_[0] }, sub { $baz } ];

print "$foo{'bar'}\n"; # red

$foo{'bar'} = "a suffusion of yellow";

print "$baz\n"; # a suffusion of yellow

=head1 DESCRIPTION

What this does is allow you to have some hash values act like they are
tied scalars without actually having to go through the trouble of making
them really be tied scalars.

By default the tied hash works exactly like a normal hash.  Its behavior
changes when you use a value of an array with exactly two code blocks in it. 
When you do this it uses the first as the get routine and the second as the
set routine.  Any future gets or sets to this key will be mediated via
these subroutines.
