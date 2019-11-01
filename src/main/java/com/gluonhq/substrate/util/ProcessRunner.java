/*
 * Copyright (c) 2019, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.substrate.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {

    private final List<String> args = new ArrayList<>();
    private final Map<String, String> map;
    private StringBuffer answer;
    private static boolean info;

    public ProcessRunner(String... args) {
        this.args.addAll(Arrays.asList(args));
        this.answer = new StringBuffer();
        this.map = new HashMap<>();
    }

    public void setInfo(boolean info) {
        this.info = info;
    }

    public void addArg(String arg) {
        args.add(arg);
    }

    public void addArgs(String... args) {
        this.args.addAll(Arrays.asList(args));
    }

    public void addArgs(Collection<String> args) {
        this.args.addAll(args);
    }

    public String getCmd() {
        return String.join(" ", args);
    }

    public void addToEnv(String key, String value) {
        map.put(key, value);
    }

    public int runProcess(String processName) throws IOException, InterruptedException {
        return runProcess(processName, null);
    }

    public int runProcess(String processName, File directory) throws IOException, InterruptedException {
        Process p = setupProcess(processName, directory);
        Thread logThread = mergeProcessOutput(p.getInputStream(), answer);
        int res = p.waitFor();
        logThread.join();
        Logger.logDebug("Result for " + processName + ": " + res);
        return res;
    }

    public boolean runTimedProcess(String processName, long timeout) throws IOException, InterruptedException {
        return runTimedProcess(processName, null, timeout);
    }

    public boolean runTimedProcess(String processName, File directory, long timeout) throws IOException, InterruptedException {
        Process p = setupProcess(processName, directory);
        Thread logThread = mergeProcessOutput(p.getInputStream(), answer);
        boolean res = p.waitFor(timeout, TimeUnit.SECONDS);
        logThread.join();
        Logger.logDebug("Result for " + processName + ": " + res);
        return res;
    }

    public String getResponse() {
        if (answer != null) {
            return answer.toString().replace("\n", "");
        }
        return null;
    }

    public List<String> getResponses() {
        return answer == null ? null :
                Arrays.asList(answer.toString().split("\n"));
    }

    public String getLastResponse() {
        if (answer == null) {
            return null;
        }
        String[] answers = answer.toString().split("\n");
        return answers.length > 0 ? answers[answers.length - 1] : "";
    }

    public static String runProcessForSingleOutput(String name, String... args) throws IOException, InterruptedException {
        ProcessRunner process = new ProcessRunner(args);
        int result = process.runProcess(name);
        if (result == 0) {
            return process.getResponse();
        }
        return null;
    }

    private Process setupProcess(String processName, File directory) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        Logger.logDebug("PB Command for " +  processName + ": " + String.join(" ", pb.command()));
        pb.redirectErrorStream(true);
        if (directory != null) {
            pb.directory(directory);
        }
        map.forEach((k, v) -> pb.environment().put(k, v));
        Logger.logDebug("Start process " + processName + "...");
        return pb.start();
    }

    private static Thread mergeProcessOutput(final InputStream is, final StringBuffer sb) {
        Runnable r = () -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println(line);
                    sb.append(line).append("\n");
                    if (info) {
                        Logger.logInfo("[SUB] " + line);
                    } else {
                        Logger.logDebug("[SUB] " + line);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        };
        Thread thread = new Thread(r);
        thread.start();
        return thread;
    }

}