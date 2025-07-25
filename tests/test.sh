#!/bin/bash

function run_file_tests() {
    FORMAT=$1

    for TEST in ${TESTS[@]} 
    do
        TESTNAME=$(basename $TEST)

        echo "### =========="
        echo "### running $TESTNAME (format=$FORMAT, options=$OPTIONS)"
        echo "... $EXEC $GROOVYSQL"

        $EXEC $GROOVYSQL                                          \
             --config  $TESTBASE/$DBCONFIG                        \
             --filein  $TESTBASE/$TESTNAME                        \
             --fileout $TESTBASE/results-$TESTNAME.$FORMAT        \
             --format $FORMAT                                     \
             --verbose $VERBOSE                                   \
             $OPTIONS

	echo "... return code = $?"
    done
}

function run_cmdline_test() {
    FORMAT=$1

    echo "### running cmdline test (format=$FORMAT, options=$OPTIONS)"
    echo "... $EXEC $GROOVYSQL"

    SQL=$(cat $TESTBASE/test.cmdline)

    $EXEC $GROOVYSQL                                         \
        --config  $TESTBASE/$DBCONFIG                        \
        --sql "$SQL"                                         \
        --format $FORMAT                                     \
        --verbose $VERBOSE                                   \
        $OPTIONS

    echo "... return code = $?"
}

function run_stdio_test() {
    FORMAT=$1

    echo "### running stdio test (format=$FORMAT, options=$OPTIONS)"
    echo "... $EXEC $GROOVYSQL"

    cat $TESTBASE/test.stdio |
    $EXEC $GROOVYSQL                                         \
        --config  $TESTBASE/$DBCONFIG                        \
        --format $FORMAT                                     \
        --verbose $VERBOSE                                   \
        $OPTIONS

    echo "... return code = $?"
}

function run_interactive_test() {

    echo "### running interactive test (options=$OPTIONS)"
    echo "... $EXEC $GROOVYSQL"

    $EXEC $GROOVYSQL                                         \
        --config  $TESTBASE/$DBCONFIG                        \
        --verbose $VERBOSE                                   \
        --interactive                                        \
        $OPTIONS

    echo "... return code = $?"
}

function run_connection_test() {

    echo "### running connection test ($FREQUENCY, options=$OPTIONS)"
    echo "... $EXEC $GROOVYSQL"

    $EXEC $GROOVYSQL                                         \
        --config  $TESTBASE/$DBCONFIG                        \
        --testconnect $FREQUENCY                             \
        $OPTIONS

    echo "... return code = $?"
}

MYNAME=~+/$0
VERBOSE=0
PROJECTBASE=$HOME/dox/repos/groovysql
GROOVYBASE=$PROJECTBASE/src/main/groovy
TESTBASE=$PROJECTBASE/tests
RUNFORMAT=classfiles
GROOVY_HOME=/usr/local/sdkman/candidates/groovy/current
GROOVYSQL_VERSION=3.0.0
PATH=$GROOVY_HOME/bin:$PATH

trap "exit 255" 1 2 3 15

cd $PROJECTBASE || exit

cd $GROOVYBASE || exit

[ -e $TESTBASE/test.cmdline ] || ( cd $TESTBASE; ln -s sqltest-1.audit test.cmdline )
[ -e $TESTBASE/test.stdio   ] || ( cd $TESTBASE; ln -s sqltest-1.audit test.stdio   )

function usage() { pod2usage -verbose 0 $MYNAME ; exit 1 ; }

while getopts :r:ie:FCST:hO:v:c: OPT
do
        case "$OPT" in
        F)      ACTION=run_file_tests ;;
        C)      ACTION=run_cmdline_test ;;
        S)      ACTION=run_stdio_test ;;
        T)      ACTION=run_connection_test ; FREQUENCY=$OPTARG ;;
        i)      ACTION=run_interactive_test ;;
	e)	DBENV="-$OPTARG" ;;
        c)      DBCONFIG=$OPTARG ;;
        r)      RUNFORMAT=$OPTARG ;;
        O)      OPTIONS+=" --$OPTARG" ;;
        v)      VERBOSE=$OPTARG ;;
        h)      pod2usage -verbose 2 $MYNAME ; exit 0 ;;
        :)      echo "$MYNAME: Option $OPTARG requires a value" >&2; exit 2 ;;
        *)      usage;;
        esac
done
shift $(( OPTIND - 1 ))

: ${DBCONFIG:="venture1.config"}
: ${DBENV:="-1"}

case $RUNFORMAT in
jar7)
        EXEC=""
        GROOVYSQL=groovysql7
        type $GROOVYSQL
        ;;
jar)
        EXEC=""
        GROOVYSQL=groovysql
        type $GROOVYSQL
        ;;
java-jar)
        EXEC="java -jar" 
        GROOVYSQL=$PROJECTBASE/build/libs/groovysql-${GROOVYSQL_VERSION}-all.jar
        ;;
classfiles7)
        EXEC="groovy"
        GROOVYSQL=net/venturechain/database/GroovySQL.groovy
        export CLASSPATH
        CLASSPATH+=:/app/d7/lib/extensions/jdbc-drivers/snowflake-1.x/snowflake-jdbc.jar
        CLASSPATH+=:/app/d7/lib/extensions/jdbc-drivers/vdp-7.0/denodo-vdp-jdbcdriver.jar
        CLASSPATH+=:/app/denodo/lib/postgresql-42.7.3.jar
        CLASSPATH+=:/app/denodo/lib/mysql-connector-j-8.4.0.jar
        CLASSPATH+=:/app/denodo/lib/v7/sqlite-jdbc-3.44.1.0.jar
        CLASSPATH+=:/app/denodo/lib/commons-csv-1.10.0.jar
        CLASSPATH+=:/app/denodo/lib/jline-3.26.1.jar
        CLASSPATH+=:/app/denodo/lib/commons-lang3-3.14.0.jar
        CLASSPATH+=:/app/denodo/lib/slf4j-nop-2.0.16.jar
      	;;
classfiles)
        EXEC="groovy"
        GROOVYSQL=net/venturechain/database/GroovySQL.groovy
        export CLASSPATH
        CLASSPATH+=:/app/d9/lib/extensions/jdbc-drivers-other/snowflake-3.x/snowflake-jdbc-3.25.1.jar
        CLASSPATH+=:/app/d9/lib/extensions/jdbc-drivers/vdp-9/denodo-vdp-jdbcdriver.jar
        CLASSPATH+=:/app/denodo/lib/postgresql-42.7.7.jar
        CLASSPATH+=:/app/denodo/lib/mysql-connector-j-9.3.0.jar
        CLASSPATH+=:/app/denodo/lib/sqlite-jdbc-3.46.0.0.jar
        CLASSPATH+=:/app/denodo/lib/commons-csv-1.10.0.jar
        CLASSPATH+=:/app/denodo/lib/jline-3.26.1.jar
        CLASSPATH+=:/app/denodo/lib/commons-lang3-3.14.0.jar
        CLASSPATH+=:/app/denodo/lib/slf4j-nop-2.0.16.jar
        ;;
*)
	echo "unrecognized runformat"
	exit 255
	;;
esac

case $ACTION in
        *file*)
                if [ $1 ]
                then TESTS=($1); shift
                else TESTS=($TESTBASE/sqltest${DBENV}.* missing_input_file)
                fi
                ;;
        *connect*)
                run_connection_test
                exit
                ;;
        *interactive*)
                run_interactive_test
                exit
                ;;
        "")
                usage
                ;;
esac

if [ $1 ]
then FORMATS=($@)
else FORMATS=(text csv xml html json)
fi

for FORMAT in ${FORMATS[@]}
do $ACTION $FORMAT
done

exit

##############################################################################

=head1 NAME

test.sh - GroovySQL test suite

=head1 SYNOPSIS

    test.sh [-h] [-FCST] [-c config] [-e dbenv] [-r runformat] [-O options] [-v level] [test] [format]

=head1 DESCRIPTION

Provides a command line interface to run the various tests for GroovySQL.

Formats supported:

  text
  csv
  html
  xml
  json

=head1 OPTIONS

=over 4

=item -h

Display this help information.

=item -F

Selects file-based tests.
By default runs all file-based tests in all formats.

=item -C

Selects command-line-based tests.
By default run a command-line-based test in all formats.

=item -S

Selects standard-input-based tests.
By default run a standard-input-based test in all formats.

=item -O options

Specifies options of "timestamps", "overwrite", and "extended".
Can be used more than once.
Extended option enables some debugging output.

=item -r runformat

Specifies a runformat of "classfiles", "java-jar", or "jar".
Specifying "classfiles" results in running groovy with the generated class files (groovy GroovySQL.groovy),
specifying "java-jar" results in running java with the generated jar file (java -jar groovysql.jar),
and specifying "jar" results in running the generated jar file (groovysql).

=item -e dbenv

Selects a set of sqltest input files.
The dbenv parameter, an integer, allows sqltest input files to be grouped by target database environment.

=item -v level

Sets verbose level.

 - Level 0 disables all messages and only displays data.
 - Level 1 enables basic informational messages (version information and open/close connection messages).
 - Level 2 adds additional information (adds open/close success, query audit).
 - Level 3 adds debug information (input trace, text format field adjustments).
 - Level 4 adds debug information (system.properties display).

=item -T frequency

Selects connection test.
Frequency set as <iterations>@<interval> where iterations are
the number of connections to be attempted and interval is
the number of milliseconds between attempts.
Iterations are required.
Interval defaults to 1000 (1 second) if not specifed.
Verbose level set to 1 by default.

=back

=head1 EXAMPLES

To run all file-based tests in all formats

  test.sh -F

To run all file-based tests in all formats using generated jar

  test.sh -r jar -F

To run a single file-based tests with JSON output

  test.sh -F customer json

To run a stdio-based test in all formats with verbose level 2, and timestamps

  test.sh -S -v 2 -O timestamps

To run a command-line test in XML format

  test.sh -C xml

To run a connection test with 5 database connections attempted at 1 second intervals

  test.sh -T 5 -v 2

  To run a connection test with 3 database connections attempted at 30 second intervals

  test.sh -T 3@30000 -v 2
