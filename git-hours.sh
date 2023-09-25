#! /bin/sh

data="$(git log "$@" --pretty=format:%ad --date=format:"%w %H" |
    awk '
        $1 == 0 { $1 = 7 }
        { h[$1 " " $2]++ }
        END {
            for (i in h)
                if (max < h[i])
                    max = h[i]
            # scale the values into the range [0..7)
            max = (max - 1) / 7
            for (i in h)
                print i " " h[i] / max
        }
    ')"

# echo "$data" >hours.txt

gnuplot <<EOF
unset key
set title "commits per hour and weekday"
set xlabel "Weekday"
set xrange [0:8]
set xtics ("" 0, "Mon" 1, "Tue" 2, "Wed" 3, "Thu" 4, "Fri" 5, "Sat" 6, "Sun" 7)
set ylabel "Hour"
set ytics 6

set terminal pngcairo
set output "hours.png"

plot '-' using 1:2:3 with points pt 6 ps variable lc 'web-blue'
$data
e
EOF

