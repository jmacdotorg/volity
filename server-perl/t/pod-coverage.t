use Test::More;
use lib 'blib/lib';
eval "use Test::Pod::Coverage 1.00";
plan skip_all => "Test::Pod::Coverage 1.00 required for testing POD coverage" if $@;
my @modules =
    Test::Pod::Coverage::all_modules();
plan tests => scalar @modules;
for ( @modules ) {
    pod_coverage_ok( $_, {}, "Pod coverage on $_" );
}
