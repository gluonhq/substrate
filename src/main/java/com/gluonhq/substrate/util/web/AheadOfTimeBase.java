/**
 * Back 2 Browser Bytecode Translator
 * Copyright (C) 2012-2018 Jaroslav Tulach <jaroslav.tulach@apidesign.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://opensource.org/licenses/GPL-2.0.
 */
package com.gluonhq.substrate.util.web;

import org.apidesign.bck2brwsr.aot.Bck2BrwsrJars;
import org.apidesign.vm4brwsr.Bck2Brwsr;
import org.apidesign.vm4brwsr.ObfuscationLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public abstract class AheadOfTimeBase<Art> {
    protected abstract File mainJavaScript();
    protected abstract File libraryPath(String fileNameJs);
    protected abstract ObfuscationLevel obfuscation();
    protected abstract String[] exports();
    protected abstract boolean ignoreBootClassPath();
    protected abstract boolean generateAotLibraries();
    protected abstract File mainJar();
    protected abstract File vm();
    protected abstract Iterable<Art> artifacts();
    protected abstract void logInfo(String msg);
    protected abstract Exception failure(String msg, Throwable cause);

    protected abstract File file(Art a);
    protected abstract Scope scope(Art a);
    protected abstract String classifier(Art a);
     protected abstract String artifactId(Art a);
    protected abstract String groupId(Art a);
    protected abstract String version(Art a);

    public final void work() {
        URLClassLoader loader;
        final Iterable<Art> artifacts = artifacts();
        artifacts.forEach(a -> logInfo(a.toString()));
        try {
            loader = buildClassLoader(mainJar(), artifacts);
        } catch (MalformedURLException ex) {
            throw raise("Can't initialize classloader", ex);
        }
        List<String> libsCp = new ArrayList<>();
        for (Art a : artifacts) {
            final File aFile = file(a);
            if (aFile == null) {
                continue;
            }
            String n = aFile.getName();
            if (!n.endsWith(".jar")) {
                continue;
            }
            if (Scope.PROVIDED == scope(a)) {
                continue;
            }
            if ("bck2brwsr".equals(classifier(a))) {
                continue;
            }
            final String libNameJs = n.substring(0, n.length() - 4) + ".js";
            File js = libraryPath(libNameJs);
            try {
                js.getParentFile().mkdirs();
                aotLibrary(a, artifacts, js, loader, libsCp);
            } catch (IOException ex) {
                throw raise("Can't compile " + aFile, ex);
            }
        }

        try {
            if (mainJavaScript().lastModified() > mainJar().lastModified()) {
                logInfo("Skipping " + mainJavaScript() + " as it already exists.");
            } else {
                logInfo("Generating " + mainJavaScript());
                Bck2Brwsr withLibsCp = Bck2Brwsr.newCompiler().library(libsCp.toArray(new String[0]));
                Bck2Brwsr c = Bck2BrwsrJars.configureFrom(withLibsCp, mainJar(), loader, ignoreBootClassPath());
                if (exports() != null) {
                    for (String e : exports()) {
                        if (e != null) {
                            c = c.addExported(e.replace('.', '/'));
                        }
                    }
                }
                try (Writer w = new OutputStreamWriter(new FileOutputStream(mainJavaScript()), "UTF-8")) {
                    c.
                            obfuscation(obfuscation()).
                            generate(w);
                }
            }
        } catch (IOException ex) {
            throw raise("Cannot generate script for " + mainJar(), ex);
        }

        try (Writer w = new OutputStreamWriter(new FileOutputStream(vm()), "UTF-8")) {
            Bck2Brwsr.newCompiler().
                    obfuscation(obfuscation()).
                    standalone(false).
                    resources(new Bck2Brwsr.Resources() {

                        @Override
                        public InputStream get(String resource) throws IOException {
                            return null;
                        }
                    }).
                    generate(w);
            w.close();
        } catch (IOException ex) {
            throw raise("Can't compile", ex);
        }
    }

    private void aotLibrary(Art a, Iterable<Art> allArtifacts, File js, URLClassLoader loader, List<String> libsCp) throws IOException {
        File aFile = file(a);
        if (js.lastModified() > aFile.lastModified()) {
            logInfo("Skipping " + js + " as it already exists.");
            libsCp.add(js.getParentFile().getName() + '/' + js.getName());
            return;
        }
        for (Art b : allArtifacts) {
            final File file = file(b);
            if ("bck2brwsr".equals(classifier(b))) { // NOI18N
                JarFile jf = new JarFile(file);
                Manifest man = jf.getManifest();
                for (Map.Entry<String, Attributes> entrySet : man.getEntries().entrySet()) {
                    String entryName = entrySet.getKey();
                    Attributes attr = entrySet.getValue();
                    if (
                        attr.getValue("Bck2BrwsrArtifactId").equals(artifactId(a)) &&
                        attr.getValue("Bck2BrwsrGroupId").equals(groupId(a)) &&
                        attr.getValue("Bck2BrwsrVersion").equals(version(a)) &&
                        "melta".equals(attr.getValue("Bck2BrwsrMagic")) &&
                        (
                            obfuscation() == ObfuscationLevel.FULL && "true".equals(attr.getValue("Bck2BrwsrMinified"))
                            ||
                            obfuscation() != ObfuscationLevel.FULL && "true".equals(attr.getValue("Bck2BrwsrDebug"))
                        )
                    ) {
                        logInfo("Extracting " + js + " from " + file);
                        libsCp.add(js.getParentFile().getName() + '/' + js.getName());
                        try (InputStream is = jf.getInputStream(new ZipEntry(entryName))) {
                            Files.copy(is, js.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                        return;
                    }
                }
            }
        }
        if (!generateAotLibraries()) {
            throw raise("Not generating " + js + " and no precompiled version found!", null);
        }
        logInfo("Generating " + js);
        libsCp.add(js.getParentFile().getName() + '/' + js.getName());
        try (Writer w = new OutputStreamWriter(new FileOutputStream(js), "UTF-8")) {
            Bck2Brwsr c = Bck2BrwsrJars.configureFrom(null, file(a), loader, ignoreBootClassPath());
            if (exports() != null) {
                c = c.addExported(exports());
            }
            c.
                    obfuscation(obfuscation()).
                    generate(w);
        }
    }
    private URLClassLoader buildClassLoader(File root, Iterable<Art> deps) throws MalformedURLException {
        List<URL> arr = new ArrayList<>();
        if (root != null) {
            arr.add(root.toURI().toURL());
        }
        for (Art a : deps) {
            if (file(a) != null) {
                arr.add(file(a).toURI().toURL());
            }
        }
        return new URLClassLoader(arr.toArray(new URL[0]), AheadOfTimeBase.class.getClassLoader());
    }

    private RuntimeException raise(String msg, Throwable cause) throws RuntimeException {
        return raise(RuntimeException.class, failure(msg, cause));
    }

    private static <E extends Exception> E raise(Class<E> type, Throwable ex) throws E {
        throw (E)ex;
    }

    public enum Scope {
        PROVIDED, RUNTIME;
    }
}
