package Volity::Player;

use warnings;
use strict;

use base qw(Class::Accessor::Fields);
use fields qw(jid name nick);

Volity::Player->create_accessors;

1;
