package net.venturechain.database

import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor

class GroovySQL {

    static void errorExit(String msg) {
        println ">>> ERROR: $msg"
        System.exit(1)
    }

    static void main(String[] args) {

        var cliOptions = new CliBuilder(usage: "groovysql [options]", posix: true,
                header: "Groovy SQL Client CLI version 2.9.0-alpha.17 (${GroovySystem.version}/${Runtime.version()})\nOptions:")

        cliOptions.with {
            a(longOpt: 'append', 'output file append mode')
            A(longOpt: 'authentication', 'specify secrets vault', args: 1)
            c(longOpt: 'config', 'specify database configuration file', args: 1)
            d(longOpt: 'database', 'specify database name', args: 1)
            f(longOpt: 'filein', 'specify input filename', args: 1)
            o(longOpt: 'fileout', 'specify output filename', args: 1)
            F(longOpt: 'format', 'specify format', args: 1)
            H(longOpt: 'csvheaders', 'output CSV headers')
            h(longOpt: 'help', 'usage information')
            i(longOpt: 'interactive', 'run in interactive mode')
            j(longOpt: 'jsonstyle', 'JSON style', args: 1)
            n(longOpt: 'node', 'specify database node', args: 1)
            p(longOpt: 'password', 'specify database password', args: 1)
            s(longOpt: 'scheme', 'specify database scheme', args: 1)
            S(longOpt: 'sql', 'specify SQL statement', args: 1, defaultValue: "")
            T(longOpt: 'testconnect', 'run connection test', args:1)
            t(longOpt: 'timestamps', 'timestamp output')
            u(longOpt: 'user', 'specify database username', args: 1)
            v(longOpt: 'verbose', 'specify verbose level', args: 1, type: int, defaultValue: "-1")
            w(longOpt: 'width', 'limit text column width', args: 1, type: int)
        }

        OptionAccessor options = cliOptions.parse(args)

        if (options.help || args.size() == 0) {
            cliOptions.usage()
            return
        }

        if (options.arguments()) {
            errorExit("unrecognized input: ${options.arguments().join(" ")}")
        }

        if (options.fileout) {
            try {
                if (new File(options.fileout).exists()) {
                    if (!options.append) {
                        errorExit("output file '$options.fileout' already exists")
                    } else if (!new File(options.fileout).canWrite()) {
                        errorExit("no write access to output file '$options.fileout'")
                    }
                } else {
                    var outFile = new File(options.fileout)
                    outFile.createNewFile()
                }
            } catch (exception) {
                errorExit("$exception (${options.fileout})")
            }
        }

        if (options.verbose >= 5) {
            println "System.properties:"
            for (key in System.properties.keySet().sort()) {
                printf('%-30s   %s\n', key, System.properties[key])
            }
        }

        Connection connection = new Connection(options)

        if (options.testconnect) {
            connection.flap(options.testconnect)
        } else if (options.sql) {
            connection.processUserInput()
        } else if (options.interactive) {
            connection.interactive()
        } else {
            connection.processFileInput()
        }

        System.exit(connection.returnCode())
    }


}
