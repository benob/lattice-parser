#!/usr/bin/perl
while(<>) {
    /^J/ || next;
    s/^J.*S=(\d+).*E=(\d+).* in="([^"]+).* out="([^"]+).*/"$1\t$2\t$3\t".uc($4)/e;
    s/ \t/\t/g;
    s/ /_/g;
    print;
}
