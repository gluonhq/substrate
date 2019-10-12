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


import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

public class Logger {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Logger.class.getName());
    private static ConsoleHandler consoleHandler;
    private static FileHandler fileHandler;

    public static void logInfo(String s) {
        LOGGER.info(s);
    }

    public static void logDebug(String s) {
        LOGGER.fine(s);
    }

    public static void logFinest(String s) {
        LOGGER.finest(s);
    }

    public static void logSevere(String s) {
        LOGGER.severe(s + "\n" +
                "Check the log files under $buildDir/client/$os-$arch/gvm/log.\n" +
                "And please check https://docs.gluonhq.com/client/ for more information.");
    }

    public static void logSevere(Throwable ex, String s) {
        logSevere(s);
        ex.printStackTrace();
        throw new RuntimeException ("Severe Error " + ex);
    }


    public static void logInit(String logPath, String message, boolean verbose) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc][%4$s] %5$s%n"); // [Date][Level] Message

        LOGGER.setLevel(Level.ALL);
        LOGGER.setUseParentHandlers(false);
        try {
            if (consoleHandler == null) {
                consoleHandler = new ConsoleHandler();
                consoleHandler.setLevel(verbose ? Level.FINE : Level.INFO);
                consoleHandler.setFormatter(new SimpleFormatter());
                LOGGER.addHandler(consoleHandler);
            }

            if (fileHandler != null) {
                LOGGER.removeHandler(fileHandler);
            }

            fileHandler = new FileHandler(logPath + "/client-debug%g.log",
                    10_485_760L, 1,
                    true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
        } catch (Exception e) {
            LOGGER.severe("Error: Logger couldn't be created");
        }
        LOGGER.fine(message);
    }

}
