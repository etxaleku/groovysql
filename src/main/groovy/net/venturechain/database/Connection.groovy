package net.venturechain.database

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.security.keyvault.secrets.SecretClient
import com.azure.security.keyvault.secrets.SecretClientBuilder
import com.azure.security.keyvault.secrets.models.KeyVaultSecret
import com.azure.security.keyvault.secrets.models.SecretProperties

import groovy.sql.Sql

import groovy.cli.commons.OptionAccessor

import groovy.json.JsonBuilder
import groovy.toml.TomlSlurper
import groovy.xml.MarkupBuilder

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.lang3.SystemUtils

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.widget.AutopairWidgets

import java.sql.SQLException

class Connection {

    var m_connection

    String m_dbHost
    String m_dbUser
    String m_dbPassword
    String m_dbName
    String m_dbClass
    String m_dbScheme
    String m_dbUrl
    String m_dbOptions

    Boolean m_timestamps

    String m_fileIn
    String m_fileOut
    Boolean m_append

    Integer m_verbose

    Boolean m_csvHeaders
    String m_jsonStyle
    String m_format
    Integer m_width

    String m_sqlStatement

    var m_dbConfigFile
    Map m_dbConfig = [:]
    String m_dbDriverVersion

    Map m_connectionParameters = [:]
    String m_authentication

    String m_historyFile
    String m_historyIgnore = "exit*:quit*:.format *:.output *:.width *:.append *"

    Integer m_returnCode

    static String timestamp() {
        return new Date().format("yyyy-MM-dd HH:mm:ss")
    }

    void displayOutput(Double level, messageObject) {
        String theLevel = String.valueOf(level)
        Integer displayLevel = theLevel.substring(0, theLevel.indexOf(".")) as Integer
        Integer displayIndent = theLevel.substring(theLevel.indexOf(".") + 1) as Integer
        if (Math.abs(m_verbose) >= displayLevel) {
            String message = messageObject
            message.replaceAll("\t", "    ").split('\n').each { String fragment ->
                if (m_timestamps) print "${timestamp()} :: "
                println " " * displayIndent + fragment
            }
        }
    }

    void errorExit(String messageObject) {
        if (m_timestamps) print "${timestamp()} :: "
        println ">>> ERROR: $messageObject"
        System.exit(2)
    }

    Integer returnCode() {
        return m_returnCode
    }

    static String getDbDriverVersion(String propertyResource, String[] props) {
        try {
            Properties appProps = new Properties().tap {
                load(Thread.currentThread().contextClassLoader.getResourceAsStream(propertyResource))
            }
            List values = []
            props.each { String property ->
                values << appProps.getProperty(property)
            }
            values.collect().join("-").replaceAll("-null", "")
        } catch (e) {
            return "0.0"
        }
    }

    Connection(OptionAccessor options) {

        m_dbConfigFile = options.config

        // load defaults from config file
        if (m_dbConfigFile) {
            m_dbConfig = new TomlSlurper().parse(new File(m_dbConfigFile))
            m_dbUser = m_dbConfig?.dbUser
            m_dbPassword = m_dbConfig?.dbPassword
            m_dbHost = m_dbConfig?.dbHost
            m_dbScheme = m_dbConfig?.dbScheme
            m_dbName = m_dbConfig?.dbName
            m_authentication = m_dbConfig?.dbAuthentication
            m_timestamps = m_dbConfig?.timestamps
            m_csvHeaders = m_dbConfig?.csvheaders
            m_jsonStyle = m_dbConfig?.jsonstyle ?: "standard"
            m_format = m_dbConfig?.format ?: "text"
            m_width = m_dbConfig?.width ?: 30
            m_verbose = m_dbConfig?.verbose ?: -1
            m_dbOptions = m_dbConfig?.dbOptions?.collect { it.value }?.join('&')
            m_dbClass = m_dbConfig?.dbClass
        }

        m_dbOptions ?= ""

        // command line option overrides config file default
        m_dbUser = options.user ?: m_dbUser
        m_dbPassword = options.password ?: m_dbPassword
        m_dbHost = options.node ?: m_dbHost
        m_dbScheme = options.scheme ?: m_dbScheme
        m_dbName = options.database ?: m_dbName
        m_authentication = options.authentication ?: m_authentication
        m_timestamps = options.timestamps ?: m_timestamps
        m_csvHeaders = options.csvheaders ?: m_csvHeaders
        m_jsonStyle = options.jsonstyle ?: (m_jsonStyle ?: "quoted")
        m_format = (options.format ?: m_format).toLowerCase()
        m_width = options.width ?: m_width
        if (options.verbose != -1) {
            m_verbose = options.verbose
        }

        // no config file settings
        m_fileIn = options.filein ?: "/dev/stdin"
        m_fileOut = options.fileout ?: "/dev/stdout"

        m_sqlStatement = (options.sql ?: "") as String

        m_returnCode = 0

        Sql.LOG.level = java.util.logging.Level.OFF     // turn off groovy.sql default logging

        switch (m_dbScheme) {
            case "vdb":
            case "denodo":
                m_dbClass ?= "com.denodo.vdp.jdbc.Driver"
                m_dbOptions ?= "queryTimeout=0" +               // 0 ms = infinite
                        "&chunkTimeout=1000" +                  // fetch flush @ 10 secs
                        "&chunkSize=5000"                       // fetch flush @ 500 rows
                m_dbOptions = "?${m_dbOptions}"
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                m_dbDriverVersion = "Denodo JDBC " +
                        getDbDriverVersion("conf/DriverConfig.properties",
                                "VDBJDBCDatabaseMetadata.driverVersion", "VDBJDBCDatabaseMetadata.driverUpdateVersion")
                break
            case "snowflake":
                m_dbClass ?= "net.snowflake.client.jdbc.SnowflakeDriver"
                m_dbName = m_dbName ? "?db=${m_dbName}" : null
                m_dbOptions ?= "queryTimeout=0"
                m_dbOptions = "&${m_dbOptions}"
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                m_dbDriverVersion = "Snowflake JDBC " +
                        getDbDriverVersion("net/snowflake/client/jdbc/version.properties", "version")
                break
            case "postgresql":
                m_dbClass ?= "org.postgresql.Driver"
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                var driver = new org.postgresql.Driver()
                m_dbDriverVersion = "Postgres JDBC ${driver.getMajorVersion()}.${driver.getMinorVersion()}"
                break
            case "mysql":
                m_dbClass ?= "com.mysql.cj.jdbc.Driver"
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                var driver = new com.mysql.cj.jdbc.Driver()
                m_dbDriverVersion = "MySQL JDBC ${driver.getMajorVersion()}.${driver.getMinorVersion()}"
                break
            case "sqlite":
                m_dbClass ?= "org.sqlite.JDBC"
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                var driver = new org.sqlite.JDBC()
                m_dbDriverVersion = "SQLite3 JDBC ${driver.getMajorVersion()}.${driver.getMinorVersion()}"
                break
            default:
                errorExit("dbscheme not recognized (${m_dbScheme})")
        }

        if (Math.abs(m_verbose) >= 1) {
            displayOutput(1, "GroovySQL SEMANTIC_VERSION powered by Groovy " +
                    "${GroovySystem.version}/${Runtime.version()} with ${m_dbDriverVersion}")
        }

        Map authProperties = [:]

        switch (m_authentication) {
            case ~/azure:.*/:
                def (_, keyVaultName, keySecretName) = m_authentication.split(':') as List
                String keyVaultUri = "https://${keyVaultName}.vault.azure.net"
                SecretClient secretClient = new SecretClientBuilder()
                        .vaultUrl(keyVaultUri)
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .buildClient()
                KeyVaultSecret keySecret = secretClient.getSecret(keySecretName)
                authProperties.user = m_dbUser
                authProperties.password = keySecret.value
                displayOutput(2, "azure keyvault authentication (keyvault: ${keyVaultName}, secret:${keySecretName})")
                break
            case ~/gcp:.*/:
                displayOutput(2, "gcp secrets manager authentication")
                break
            case ~/aws:.*/:
                displayOutput(2, "aws secrets authentication")
                break
            case ~/keypair:.*/:
                def (_, keyFile, keyPassword) = m_authentication.split(':') as List
                if (!new File(keyFile).canRead()) {
                    throw new IllegalArgumentException("unable to read private key file: ${keyFile}")
                }
                authProperties.private_key_file = keyFile
                if (keyPassword) {
                    authProperties.private_key_pwd = keyPassword
                    authProperties.private_key_security = "encrypted"
                } else {
                    authProperties.private_key_security = "unencrypted"
                }
                displayOutput(2, "keypair authentication with ${authProperties.private_key_security} private_key_file = ${keyFile}")
                break
            default:
                authProperties.user = m_dbUser
                authProperties.password = m_dbPassword
                displayOutput(2, "basic authentication")
        }

        m_connectionParameters = [
                url       : "${m_dbUrl}${m_dbOptions}",
                driver    : m_dbClass,
                properties: authProperties as Properties
        ]

        if (!options.testconnect) {
            displayOutput(1, "opening connection to ${m_dbUrl}")

            m_dbOptions?.tokenize('?&')?.each {
                displayOutput(2, "dbOptions: $it")
            }

            try {
                m_connection = Sql.newInstance(m_connectionParameters)
                displayOutput(2, "successfully opened connection to ${m_dbUrl}")
            } catch (SQLException sqlException) {
                displayOutput(0, ">>> ERROR: unable to open dbconnection to ${m_dbUrl}:")
                displayOutput(0.4, sqlException)
                displayOutput(0.4, "user=${m_dbUser}")
                System.exit(1)
            }
        }
    }

    void closeConnection() {
        displayOutput(1, "closing connection to ${m_dbUrl}")
        m_connection.close()
        displayOutput(2, "successfully closed connection to ${m_dbUrl}")
    }

    void flap(frequency) {
        displayOutput(1, "testing connection to ${m_dbUrl}")
        m_dbOptions?.tokenize('?&')?.each {
            displayOutput(2, "dbOptions: $it")
        }
        List tokens = frequency.split("@")
        Integer iterations = tokens[0] as Integer
        Integer interval = tokens[1] as Integer
        interval = (interval ?: 1)
        for (Integer iteration = 1; iteration <= iterations; ++iteration) {
            displayOutput(1, ">>> iteration ${iteration} of ${iterations} with a ${interval} sec delay")
            m_connection = Sql.newInstance(m_connectionParameters)
            displayOutput(1, "opened connection to ${m_dbUrl}")
            displayOutput(1, "sending query")
            m_connection.eachRow("select 1") {
                displayOutput(1, "processed and discarded result successfully")
            }
            closeConnection()
            if (iteration < iterations) {
                displayOutput(1, "waiting ${interval} sec")
                Thread.sleep(interval * 1000)
            }
        }
    }

    void formatTextResults(resultSet, columnNames, colWidths, colTypes) {

        // limit each column display to a max of width bytes
        colWidths.eachWithIndex { var width, int columnIndex ->
            if (m_verbose >= 4) print "column '${columnNames[columnIndex]}' width = $width (width option=$m_width)"
            colWidths[columnIndex] = Math.min(width, m_width)
            if (m_verbose >= 4) println "... set to ${colWidths[columnIndex]}" +
                    ((colTypes[columnIndex] in [-6, -5, 2, 3, 4, 5, 6, 7, 8]) ? " right " : " left ") +
                    "justified (type=${colTypes[columnIndex]})"
        }

        new FileWriter(m_fileOut, true).withWriter { writer ->

            // output column heading, limit heading width to field width as sql may not
            columnNames.eachWithIndex { var columnName, int columnIndex ->
                var limit = (colWidths[columnIndex] > m_width) ? m_width : colWidths[columnIndex]
                writer.printf("%-${colWidths[columnIndex]}.${limit}s ", columnName)
            }
            writer.write("\n")

            // output column heading underline
            columnNames.eachWithIndex { var columnName, int columnIndex ->
                writer.write("-" * colWidths[columnIndex] + " ")
            }
            writer.write("\n")

            // output result data
            String formatString
            resultSet.each { rowResult ->
                rowResult.eachWithIndex { var entry, int columnIndex ->
                    if (rowResult[columnIndex] == null) {
                        writer.printf("%-${colWidths[columnIndex]}.${m_width}s ", " ")
                    } else {
                        formatString = switch (colTypes[columnIndex]) {
                            case -6..-5 -> "%${colWidths[columnIndex]}d "             // tinyint, bigint
                            case 2 -> "%${colWidths[columnIndex]}.0f "                // numeric
                            case 3 -> "%${colWidths[columnIndex]}.2f "                // decimal
                            case 4..5 -> "%${colWidths[columnIndex]}d "               // integer, smallint
                            case 6..7 -> "%${colWidths[columnIndex]}.4f "             // float, real
                            case 8 -> "%${colWidths[columnIndex]}.1f "                // double
                            default -> "%-${colWidths[columnIndex]}.${m_width}s "     // everything else
                        }
                        try {
                            writer.printf(formatString, rowResult[columnIndex])
                        } catch (exception) {
                            // in rare circumstances some bigint columns need floating point formatting
                            formatString = "%${colWidths[columnIndex]}.0f "
                            writer.printf(formatString, rowResult[columnIndex])
                        }
                    }
                }
                writer.write("\n")
            }
        }
    }

    void formatCSVResults(resultSet, columnNames) {
        new FileWriter(m_fileOut, true).withWriter { writer ->
            new CSVPrinter(writer, CSVFormat.DEFAULT).with {
                if (m_csvHeaders) printRecord(columnNames)
                resultSet.each { rowResult ->
                    printRecord(rowResult.values())
                }
            }
        }
    }

    void formatXMLResults(resultSet, columnNames) {
        new FileWriter(m_fileOut, true).withWriter { writer ->
            new MarkupBuilder(writer).with {
                rows {
                    resultSet.eachWithIndex { var rowResult, int rowid ->
                        row {
                            mkp.comment("row: ${rowid + 1}")
                            columnNames.each { columnName ->
                                "$columnName"(rowResult[columnName])
                            }
                        }
                    }
                }
            }
            writer.write("\n")
        }
    }

    void formatHTMLResults(resultSet, columnNames) {
        new FileWriter(m_fileOut, true).withWriter { writer ->
            new MarkupBuilder(writer).with {
                table {
                    thead {
                        tr {
                            columnNames.each { columnName ->
                                th(columnName)
                            }
                        }
                    }
                    tbody {
                        resultSet.each { rowResult ->
                            tr {
                                columnNames.each { columnName ->
                                    td(rowResult[columnName])
                                }
                            }
                        }
                    }
                }
            }
            writer.write("\n")
        }
    }

    void formatJSONResults(resultSet, columnNames) {

        var json = new JsonBuilder()

        switch (m_jsonStyle) {
            case "quoted": // alternative 1: (quotes all values)
                json {
                    rows(
                            resultSet.collect { rowResult ->
                                columnNames.collectEntries { columnName ->
                                    [columnName, rowResult[columnName] as String]
                                }
                            }
                    )
                }
                break

            case "standard": // alternative 2: (doesn't quote numeric values)
                json {
                    rows(
                            resultSet.collect { row ->
                                row.subMap(columnNames)
                            }
                    )
                }
                break

            case "spread": // alternative 3: (doesn't quote numeric values, uses spread operator)
                json {
                    rows(
                            resultSet*.subMap(columnNames)
                    )
                }
                break
        }

        new FileWriter(m_fileOut, true).withWriter { writer ->
            writer.write(json.toPrettyString())
            writer.write("\n")
        }
    }

    void processCommandInput(String command) {
        // process .command file input
        displayOutput(1, "processing command input: $command")
        List tokens = command.split(" ")
        switch (tokens[0]) {
            case ".output":
                m_fileOut = tokens[1]
                if (new File(m_fileOut).exists() && !m_append) {
                    errorExit("file $m_fileOut already exists")
                }
                displayOutput(1, "output file set to: $m_fileOut")
                break
            case ".format":
                m_format = tokens[1]
                displayOutput(1, "output format set to: $m_format")
                break
            case ".json":
                m_jsonStyle = tokens[1]
                displayOutput(1, "json style set to: $m_jsonStyle")
                break
            case ".append":
                m_append = tokens[1]
                displayOutput(1, "append set to: $m_append")
                break
            case ".width":
                m_width = tokens[1] as Integer
                displayOutput(1, "width set to: $m_width")
                break
            case ".remove":
                new File(tokens[1] as String).delete()
                displayOutput(1, "removed file: ${tokens[1]}")
                break
            default:
                errorExit("unrecognized command input: $command")
                break
        }
    }

    void processSQL(String sqlStatement) {

        List data = []
        List colNames = []
        List colWidths = []
        List colTypes = []

        var metaClosure = { metadata ->
            colNames = (1..metadata.columnCount).collect { metadata.getColumnLabel(it) }
            colWidths = (1..metadata.columnCount).collect { metadata.getColumnDisplaySize(it) }
            colTypes = (1..metadata.columnCount).collect { metadata.getColumnType(it) }
        }

        displayOutput(3, "executing: $sqlStatement")

        try {
            m_connection.execute(sqlStatement, metaClosure) { isResultSet, result ->
                if (isResultSet) {
                    data = result
                } else {
                    if (Math.abs(m_verbose) > 0) displayOutput(0, "updated rowcount: $result")
                }
            }
        } catch (exception) {
            m_returnCode = 3
            displayOutput(0, ">>> ERROR:")
            displayOutput(0.4, exception)
            displayOutput(0, " ")
            return
        }

        if (data.size() == 0) {
            return
        }

        switch (m_format) {
            case "text":
                formatTextResults(data, colNames, colWidths, colTypes)
                break
            case "csv":
                formatCSVResults(data, colNames)
                break
            case "html":
                formatHTMLResults(data, colNames)
                break
            case "xml":
                formatXMLResults(data, colNames)
                break
            case "json":
                formatJSONResults(data, colNames)
                break
            default:
                displayOutput(0, "unrecognized format: $m_format")
                break
        }
    }

    void processUserInput() {
        // handle sql command line input
        processSQL(m_sqlStatement)
        m_sqlStatement = ""
        if (m_fileOut != "/dev/stdout") displayOutput(1, "output: $m_fileOut")
        closeConnection()
    }

    void processFileInput() {
        // handle file input
        if (m_fileIn != "/dev/stdin" && !new File(m_fileIn).exists()) {
            errorExit("input file not found: $m_fileIn")
        }

        new FileReader(m_fileIn).withReader { reader ->
            reader.eachLine { line, lineNumber ->
                displayOutput(4, sprintf('input line %2d: %s', lineNumber, line))
                if (line.startsWith(".")) {
                    displayOutput(4, ">>> line $lineNumber: CONTROL RECORD: $line")
                    if (m_sqlStatement != "")
                        errorExit("(line $lineNumber) discarding unterminated SQL = $m_sqlStatement")

                    processCommandInput(line)

                } else if (line ==~ /.*[^\\];\s*$|^;/) {
                    // non-escaped semicolon followed by only whitespace to eol
                    m_sqlStatement += "\n$line"

                    processSQL(m_sqlStatement)

                    m_sqlStatement = ""
                } else {
                    m_sqlStatement += "\n$line"
                }
            }
        }

        if (m_fileOut != "/dev/stdout") displayOutput(1, "output: $m_fileOut")
        if (m_sqlStatement != "") errorExit("discarding unterminated SQL = $m_sqlStatement")
        closeConnection()
    }

    void interactive() {

        m_historyFile = "${SystemUtils.getUserHome()}/.groovysql_history"

        Terminal terminal = TerminalBuilder.terminal()

        DefaultParser parser = new DefaultParser()

        parser.setEofOnUnclosedBracket(DefaultParser.Bracket.CURLY,
                DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE)

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter("select", "insert", "update", "delete", "create", "drop"))
                .parser(parser)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
                .variable(LineReader.INDENTATION, 2)
                .option(LineReader.Option.INSERT_BRACKET, true)
                .variable(LineReader.HISTORY_FILE, m_historyFile)
                .variable(LineReader.HISTORY_FILE_SIZE, 10_000)
                .variable(LineReader.HISTORY_IGNORE, m_historyIgnore)
                .build()

        AutopairWidgets autopairWidgets = new AutopairWidgets(reader, true)

        autopairWidgets.enable()

        String line

        while (true) {

            try {
                line = reader.readLine("> ")
                if (line == null || line.equalsIgnoreCase("quit")) {
                    break
                }
            } catch (exception) {
                if (exception == EndOfFileException) {
                    println "eof"; return
                }   //FIXME
            }

            reader.getHistory().add(line)

            if (line.startsWith(".")) {
                processCommandInput(line)
            } else if (line ==~ /.*[^\\];\s*$|^;/) {
                // statement terminated (non-escaped semicolon followed by only whitespace to eol)
                m_sqlStatement += line

                processSQL(m_sqlStatement)

                m_sqlStatement = ""
            } else {
                m_sqlStatement += "$line\n"
            }

            reader.getHistory().save()
        }
    }
}
