# haxe-maven-plugin
Maven plugin to transpile haxe sources and ouput them for later consumption. 

Often in complex projects it would be nice to insert Haxe. A perfect example of this is JavaScript. Using Haxe as a JavaScript replacement is a great way to write cleaner (type safe) code.

However, including these generated files into a project is at best ill advised and at worst not allowed (especially in corporate environments). Alot of corporate build system as dependency resolutions are based on Maven. It is also very common that a build machine does no have Haxe installed.

This plugin address that by:

* Downloading Node.js (used by lix)
* Copying Haxe sources to an intermediate directory
* Scaffolding a basic "lix" project 
* Installing "lix" locally
* Building Haxe sources locally using "lix"
* Copy the resulting output files

## Basic usage (pom.xml):

```xml
  <build>
    <plugins>
      <plugin>
        <groupId>haxe.plugin</groupId>
        <artifactId>haxe-maven-plugin</artifactId>
        <version>1.0</version>
        <executions>
          <execution>
            <phase>compile</phase>
            <goals>
              <goal>transpile</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
            <!--
            <haxeVersion>latest</haxeVersion>
            <hxmlFile>build.hxml</hxmlFile>
            <mainClass>Main</mainClass>
            <outputDirectory>${project.basedir}/haxe-output</outputDirectory>
            -->
            <haxeTarget>cpp</haxeTarget>
            <classPaths>
                <classPath>src</classPath>
            </classPaths>
            <haxelibs>
                <haxelib>test</haxelib>
            </haxelibs>
            <compilerArgs>
                <compilerArg>--times</compilerArg>
            </compilerArgs>
            <compilerProps> <!-- -D abc -->
                <compilerProp>no-compilation</compilerProp>
            </compilerProps>
        </configuration>
      </plugin>
    </plugins>
  </build>

```

