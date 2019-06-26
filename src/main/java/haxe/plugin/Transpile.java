package haxe.plugin;

import haxe.plugin.util.HxmlParser;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Mojo(name = "transpile", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class Transpile extends AbstractMojo {
    @Parameter(defaultValue = "10.16.0", property="node.version", required=true)
    private String nodeVersion;

    @Parameter(defaultValue = "x64", property="node.architecture", required=true)
    private String nodeArchitecture;

    @Parameter(defaultValue = "stable", property="haxeVersion", required = true)
    private String haxeVersion;

    @Parameter(property = "hxmlFile")
    private String haxeHxml;

    @Parameter(defaultValue = "js", property="haxeTarget")
    private String haxeTarget;

    @Parameter(property = "mainClass")
    private String haxeMain;

    @Parameter
    private List<String> classPaths;

    @Parameter
    private List<String> haxelibs;

    @Parameter
    private List<String> compilerArgs;

    @Parameter
    private List<String> compilerProps;

    @Parameter( defaultValue = "${project.build.directory}", property = "intermediateDirectory", required = true )
    private File intermediateDirectory;

    @Parameter( defaultValue = "${project.basedir}/src/main/haxe", property = "sourceDirectory", required = true )
    private File sourceDirectory;

    @Parameter( defaultValue = "${project.basedir}/haxe-output", property = "outputDirectory", required = true )
    private File outputDirectory;

    public void execute() throws MojoExecutionException {
        getLog().info("nodeVersion: " + nodeVersion);
        getLog().info("nodeArchitecture: " + nodeArchitecture);
        getLog().info("platform: " + getNodeOS());
        getLog().info("haxeVersion: " + haxeVersion);
        getLog().info("intermediateDir: " + intermediateDirectory);
        getLog().info("sourceDirectory: " + sourceDirectory);
        getLog().info("outputDirectory: " + outputDirectory);

        try {
            downloadNode();
            printNodeVersion();
            copySources();
            createPackageJson();
            installLix();
            createLixScope();
            buildSources();
            copyHaxeOutput();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new MojoExecutionException("Failed", ex);
        }
    }

    private void buildSources() throws Exception {
        HxmlParser hxml = new HxmlParser();
        if (haxeHxml != null) {
            hxml = HxmlParser.parse(new File(sourceOutputDir(), haxeHxml));
        } else if (haxeMain == null) {
            haxeMain = "Main";
        }

        hxml.addClassPaths(classPaths);
        hxml.addHaxelibs(haxelibs);

        if (haxeMain != null) {
            hxml.setMainClass(haxeMain);
        }
        hxml.fixTarget(haxeTarget, intermediateHaxeOutputDir());

        if (haxeTarget.equals("cpp")) {
            hxml.addHaxelib("hxcpp");
        }

        for (String lib : hxml.getHaxelibs()) {
            lix("install haxelib:" + lib);
        }

        StringBuffer sb = new StringBuffer();
        for (HxmlParser.HxmlParam param : hxml.params) {
            sb.append("-");
            sb.append(param.name);
            sb.append(" ");
            sb.append(param.value);
            sb.append(" ");
        }

        for (String s : compilerArgs) {
            sb.append(s);
            sb.append(" ");
        }

        for (String s : compilerProps) {
            sb.append("-D ");
            sb.append(s);
            sb.append(" ");
        }

        haxe("" + sb.toString().trim());
    }

    private void copyHaxeOutput() throws Exception {
        FileUtils.copyDirectory(intermediateHaxeOutputDir(), haxeOutputDir());
    }

    private void createLixScope() throws Exception {
        lix("scope create");
        lix("install haxe " + haxeVersion);
        lix("use haxe " + haxeVersion);
        haxe("-version");
    }

    private void installLix() throws Exception {
        npm("install lix --save --scripts-prepend-node-path", sourceOutputDir());
    }

    private void copySources() throws Exception {
        if (sourceDirectory.exists() == false) {
            return;
        }
        File srcDir = sourceDirectory;
        File dstDir = sourceOutputDir();
        FileUtils.copyDirectory(srcDir, dstDir);
    }

    private File sourceOutputDir() {
        File f = new File(intermediateDirectory, "haxe-sources");
        f.mkdirs();
        return f;
    }

    private File intermediateHaxeOutputDir() {
        File f = new File(intermediateDirectory, "haxe-output");
        f.mkdirs();
        return f;
    }

    private File haxeOutputDir() {
        File f = outputDirectory;
        f.mkdirs();
        return f;
    }

    private void createPackageJson() throws Exception {
        File dir = sourceOutputDir();
        File file = new File(dir, "package.json");
        if (!file.exists()) {
            FileUtils.writeStringToFile(file, "{}");
        }
        npm("init -y", sourceOutputDir());
    }

    private void printNodeVersion() throws Exception {
        node("--version");
        npm("--version");
        npx("--version");
    }

    private int haxe(String command) throws Exception {
        return npx("haxe " + command, sourceOutputDir());
    }

    private int lix(String command) throws Exception {
        return npx("lix " + command, sourceOutputDir());
    }

    private int node(String command) throws Exception {
        File nodeOutputDir = new File(getNodeOutputDir());
        File nodeDir = new File(nodeOutputDir, getNodeFileName());
        String node = nodeDir + File.separator + "node";
        getLog().info("Using node from: " + nodeDir);

        int result = exec(node + " " + command, nodeDir);
        return result;
    }

    private int npm(String command) throws Exception {
        return npm(command, null);
    }

    private int npm(String command, File dir) throws Exception {
        File nodeOutputDir = new File(getNodeOutputDir());
        File nodeDir = new File(nodeOutputDir, getNodeFileName());
        if (dir == null) {
            dir = nodeDir;
        }
        String npm = nodeDir + File.separator + "npm";
        if (SystemUtils.IS_OS_WINDOWS) {
            npm += ".cmd";
        }
        getLog().info("Using npm from: " + nodeDir);

        int result = exec(npm + " " + command, dir);
        return result;
    }

    private int npx(String command) throws Exception {
        return npx(command, null);
    }

    private int npx(String command, File dir) throws Exception {
        File nodeOutputDir = new File(getNodeOutputDir());
        File nodeDir = new File(nodeOutputDir, getNodeFileName());
        if (dir == null) {
            dir = nodeDir;
        }

        String npx = nodeDir + File.separator + "npx";
        if (SystemUtils.IS_OS_WINDOWS) {
            npx += ".cmd";
        }
        getLog().info("Using npx from: " + nodeDir);

        int result = exec(npx + " " + command, dir);
        return result;
    }

    private void downloadNode() throws Exception {
        File nodeOutputDir = new File(getNodeOutputDir());
        if (!nodeOutputDir.exists()) {
            nodeOutputDir.mkdirs();
        }

        File file = new File(nodeOutputDir, getNodeZipFileName());
        // download
        if (!file.exists()) {
            getLog().info("Downloading " + file + " from " + getNodeDownloadURL());
            FileUtils.copyURLToFile(new URL(getNodeDownloadURL()), file);
        } else {
            getLog().info("Archive found: " + file + " (skipping download)");
        }

        // unzip
        File nodeDir = new File(nodeOutputDir, getNodeFileName());
        if (!nodeDir.exists() || nodeDir.listFiles().length == 0) {
            getLog().info("Unzipping " + file + " to " + getNodeOutputDir());
            unzip(file, getNodeOutputDir());
        } else {
            getLog().info("Expanded archive found: " + getNodeOutputDir() + " (skipping unzip)");
        }
    }

    private String getNodeDownloadURL() {
        String url = "https://nodejs.org/dist/v" + nodeVersion + "/node-v" + nodeVersion + "-" + getNodeOS() + "-" + nodeArchitecture + "." + getNodeOSZipExtension();
        return url;
    }

    private String getNodeOutputDir() {
        return new File(intermediateDirectory, "node").toString();
    }

    private String getNodeFileName() {
        String file = "node-v" + nodeVersion + "-" + getNodeOS() + "-" + nodeArchitecture;
        return file;
    }

    private String getNodeZipFileName() {
        String file = getNodeFileName() + "." + getNodeOSZipExtension();
        return file;
    }

    private String getNodeOS() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "win";
        } else if (SystemUtils.IS_OS_LINUX) {
            return "linux";
        } else if (SystemUtils.IS_OS_MAC) {
            return "darwin";
        }
        return null;
    }

    private String getNodeOSZipExtension() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "zip";
        } else if (SystemUtils.IS_OS_LINUX) {
            return "tar.xz";
        } else if (SystemUtils.IS_OS_MAC) {
            return "tar.gz";
        }
        return null;
    }

    public void unzip(File zipFile, String outputFolder) throws Exception {
        byte[] buffer = new byte[1024];
        try {
            //create output directory is not exists
            File folder = new File(outputFolder);
            if(!folder.exists()){
                folder.mkdir();
            }

            //get the zip file content
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while(ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                getLog().info("Unzipping: " + newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                if (!ze.isDirectory()) {
                    FileOutputStream fos = new FileOutputStream(newFile);

                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }

                    fos.close();
                }
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

            getLog().info("Done");
        } catch(IOException ex) {
            throw ex;
        }
    }

    public String execToString(String command, File workingDirectory) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CommandLine commandline = CommandLine.parse(command);
        DefaultExecutor exec = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        exec.setStreamHandler(streamHandler);
        exec.setWorkingDirectory(workingDirectory);
        exec.execute(commandline);
        return(outputStream.toString());
    }

    public int exec(String command, File workingDirectory) throws Exception {
        getLog().info("Executing: '" + command + "' (" + workingDirectory + ")");
        CommandLine commandline = CommandLine.parse(command);
        DefaultExecutor exec = new DefaultExecutor();
        exec.setStreamHandler(new PumpStreamHandler(new ExecOutputStream(getLog()), new ExecOutputStream(getLog())));
        exec.setWorkingDirectory(workingDirectory);
        int result = exec.execute(commandline);
        getLog().info("exec: result = " + result);
        return result;
    }

    private static class ExecOutputStream extends LogOutputStream {
        private Log log;
        public ExecOutputStream(Log log) {
            this.log = log;
        }
        @Override
        protected void processLine(String line, int level) {
            log.info("exec: " + line);
        }
    }
}
