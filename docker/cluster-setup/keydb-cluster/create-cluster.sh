DB1=$(host db-1 | awk '/has address/ { print $4 ; exit }')
DB2=$(host db-2 | awk '/has address/ { print $4 ; exit }')
DB3=$(host db-3 | awk '/has address/ { print $4 ; exit }')
DB4=$(host db-4 | awk '/has address/ { print $4 ; exit }')
DB5=$(host db-5 | awk '/has address/ { print $4 ; exit }')
DB6=$(host db-6 | awk '/has address/ { print $4 ; exit }')

keydb-cli --cluster create ${DB1}:7000 ${DB2}:7000 ${DB3}:7000 ${DB4}:7000 ${DB5}:7000 ${DB6}:7000 --cluster-replicas 1