package haxe.plugin.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class HxmlParser {
    public static class HxmlParam {
        public String name;
        public String value;
    }

    public List<HxmlParam> params = new ArrayList<HxmlParam>();

    public void parse(String content) throws Exception {
        String lines[] = content.split(Pattern.quote("\n"));
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }

            String parts[] = line.split(Pattern.quote(" "));
            HxmlParam param = new HxmlParam();
            if (parts.length >= 1) {
                if (parts[0].startsWith("#")) {
                    continue;
                }
                param.name = parts[0].substring(1).trim();
            }
            if (parts.length >= 2) {
                param.value = parts[1].trim();
            }
            if (param.name != null) {
                params.add(param);
            }
        }
    }

    public boolean hasClassPath(String path) {
        for (HxmlParser.HxmlParam param : params) {
            if (param.equals("cp") && param.value.equals(path)) {
                return true;
            }
        }

        return false;
    }

    public void addClassPaths(List<String> paths) {
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            if (hasClassPath(path) == false) {
                HxmlParam param = new HxmlParam();
                param.name = "cp";
                param.value = path;
                params.add(param);
            }
        }
    }

    public boolean hasHaxelib(String lib) {
        for (HxmlParser.HxmlParam param : params) {
            if (param.equals("lib") && param.value.equals(lib)) {
                return true;
            }
        }

        return false;
    }

    public void addHaxelib(String lib) {
        if (hasHaxelib(lib) == false) {
            HxmlParam param = new HxmlParam();
            param.name = "lib";
            param.value = lib;
            params.add(param);
        }
    }

    public void addHaxelibs(List<String> libs) {
        if (libs == null) {
            return;
        }
        for (String lib : libs) {
            if (hasHaxelib(lib) == false) {
                HxmlParam param = new HxmlParam();
                param.name = "lib";
                param.value = lib;
                params.add(param);
            }
        }
    }

    public List<String> getHaxelibs() {
        List<String> libs = new ArrayList<String>();
        for (HxmlParser.HxmlParam param : params) {
            if (param.name.equals("lib")) {
                libs.add(param.value);
            }
        }
        return libs;
    }

    public void fixTarget(String target, File outputDir) {
        if (target == null) {
            return;
        }

        if (getTarget() == null) {
            String mainClass = getMainClassName();
            if (mainClass != null) {
                String ext = targetToExtension(target);
                HxmlParam param = new HxmlParam();
                param.name = target;
                if (ext.length() != 0) {
                    param.value = new File(outputDir, mainClass + "." + ext).toString();
                } else {
                    param.value = outputDir.toString();
                }
                params.add(param);
            }
            return;
        }

        for (HxmlParser.HxmlParam param : params) {
            if (param.name.equals(target)) {
                File f = new File(param.value);
                switch (target) {
                    case "js": // these outputs have a file name
                    case "swf":
                    case "python":
                    case "lua":
                    case "neko":
                    case "hl":
                            param.value = new File(outputDir, f.getName()).toString();
                        break;

                    default: // the rest are just output dirs
                        break;
                }
            }
        }
    }

    public static final String[] HAXE_TARGETS = {"js", "php", "cpp", "java", "cs", "swf", "python", "lua", "neko", "hl"};
    public String getTarget() {
        String target = null;
        for (HxmlParser.HxmlParam param : params) {
            if (Arrays.asList(HAXE_TARGETS).indexOf(param.name) != -1) {
                target = param.value;
                break;
            }
        }
        return target;
    }

    public String targetToExtension(String target) {
        switch (target) {
            case "js":
                return "js";
            case "swf":
                return "swf";
            case "python":
                return "py";
            case "lua":
                return "lua";
            case "neko":
                return "n";
            case "hl":
                return "hl";
        }

        return "";
    }

    public String getMainClassName() {
        String mainClass = null;
        for (HxmlParser.HxmlParam param : params) {
            if (param.name.equals("main")) {
                mainClass = param.value;
                String[] parts = mainClass.split(Pattern.quote("."));
                mainClass = parts[parts.length - 1];
                break;
            }
        }
        return mainClass;
    }

    public void setMainClass(String value) {
        if (getMainClassName() == null) {
            HxmlParam param = new HxmlParam();
            param.name = "main";
            param.value = value;
            params.add(param);
            return;
        }
        for (HxmlParser.HxmlParam param : params) {
            if (param.name.equals("main")) {
                param.value = value;
                break;
            }
        }
    }

    public static HxmlParser parse(File file) throws Exception {
        if (file.exists() == false) {
            return new HxmlParser();
        }
        String content = FileUtils.readFileToString(file);

        HxmlParser hxml = new HxmlParser();
        hxml.parse(content);;

        return hxml;
    }
}
