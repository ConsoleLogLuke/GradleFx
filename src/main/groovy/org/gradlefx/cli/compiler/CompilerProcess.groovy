package org.gradlefx.cli.compiler

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Executes the compiler itself.
 */
interface CompilerProcess {

    CompilerResult compile()
}

/**
 * Ant-based compiler process implementation that executes a compiler's jar file with Ant.
 */
class AntBasedCompilerProcess implements CompilerProcess {

    private static final Logger LOG = LoggerFactory.getLogger 'gradlefx'

    private AntBuilder ant
    private CompilerJar compilerJar
    private File flexHome
    private File temporaryDir
    private List<String> jvmArguments = []
    private CompilerOptions compilerOptions = []
    private CompilerResultHandler compilerResultHandler = null

    AntBasedCompilerProcess(AntBuilder ant, CompilerJar compilerJar, File flexHome, File temporaryDir) {
        this.ant = ant
        this.compilerJar = compilerJar
        this.flexHome = flexHome
        this.temporaryDir = temporaryDir
    }

    void setJvmArguments(List<String> jvmArguments) {
        this.jvmArguments = jvmArguments
        this
    }

    void setCompilerOptions(CompilerOptions compilerOptions) {
        this.compilerOptions = compilerOptions
        this
    }

    void setCompilerResultHandler(CompilerResultHandler compilerResultHandler) {
        this.compilerResultHandler = compilerResultHandler
    }

    private void moveResourcesToFile() {
        List<String> xmlLines = ['<?xml version="1.0" encoding="UTF-8" ?>',
                                 '<flex-config xmlns="http://www.adobe.com/2006/flex-config">']
        compilerOptions.asList().eachWithIndex { option, index ->
            if (option != "-include-file") {
                return
            }
            compilerOptions.getArguments().remove(index)
            String fileName = compilerOptions.getArguments().remove(index)
            String filePath = compilerOptions.getArguments().remove(index)

            xmlLines.add("<include-file>")
            xmlLines.add("<name>$fileName</name>")
            xmlLines.add("<path>$filePath</path>")
            xmlLines.add("</include-file>")
        }

        if (xmlLines.size() == 2) {
            return
        }
        xmlLines.add("</flex-config>")

        String xmlString = xmlLines.join("")
        temporaryDir.createTempFile("flex-resources", ".xml").with {
            deleteOnExit()
            write(xmlString)
            compilerOptions.add(CompilerOption.LOAD_CONFIG, absolutePath)
        }
    }

    @Override
    CompilerResult compile() {
        moveResourcesToFile()

        LOG.info "Compiling with " + compilerJar.name()
        compilerOptions.each {
            LOG.info "\t$it"
        }

        String antResultProperty = compilerJar.name() + 'Result'
        String antOutputProperty = compilerJar.name() + 'Output'
        String antErrorProperty = compilerJar.name() + 'Error'

        ant.java(
                jar: "$flexHome.absolutePath/lib/${compilerJar}",
                dir: "$flexHome.absolutePath/frameworks",
                fork: true,
                resultproperty: antResultProperty,
                outputproperty: antOutputProperty,
                errorproperty: antErrorProperty,
                failOnError: false) {
            jvmArguments.each {
                jvmarg(value: it)
            }
            compilerOptions.each {
                arg(value: it)
            }
        }

        boolean isSuccess = ant.properties[antResultProperty] == '0'
        String output = ant.properties[antOutputProperty]
        String errorStr =  ant.properties[antErrorProperty]
        def compilerResult = new CompilerResult(isSuccess, compilerJar, output, errorStr)

        if(compilerResultHandler) {
            compilerResultHandler.handleResult(compilerResult)
        }

        compilerResult
    }
}

/**
 * The result of a compilation process.
 */
class CompilerResult {

    boolean success
    CompilerJar compilerJar
    String compilerLog
    String errorLog

    CompilerResult(boolean success, CompilerJar compilerJar, String compilerLog, String errorLog) {
        this.success = success
        this.compilerJar = compilerJar
        this.compilerLog = compilerLog
        this.errorLog = errorLog
    }
}

/**
 * Handles the result of a compilation process.
 */
interface CompilerResultHandler {
    void handleResult(CompilerResult result)
}
