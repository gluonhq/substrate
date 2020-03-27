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
#import <UIKit/UIKit.h>
#include <stdarg.h>

static __inline__ void gvmlog(NSString* format, ...)
{
    va_list argList;
    va_start(argList, format);
    NSString* formattedMessage = [[NSString alloc] initWithFormat: format arguments: argList];
    va_end(argList);
    fprintf(stderr, "[GVM] %s\n", [formattedMessage UTF8String]);
    [formattedMessage release];
}

#ifdef GVM_VERBOSE
    #define gvmlog(MSG, ...) gvmlog(MSG, ## __VA_ARGS__ )
#else
    #define gvmlog(MSG, ...) (void) 0
#endif

@interface AppDelegate : UIResponder <UIApplicationDelegate>

@property (strong, nonatomic) UIWindow *window;


@end

int startGVM(const char* userHome);

extern int *run_main(int argc, const char* argv[]);

@interface AppDelegate ()

@end

int main(int argc, char * argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([AppDelegate
 class]));
    }
}


@implementation AppDelegate

-(void)startVM:(id)selector {
    gvmlog(@"Starting vm...");
    NSArray *documentPaths = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES);
    NSString *documentsDir = [documentPaths objectAtIndex:0];
    NSString *folder = @"gluon";
    NSString *folderPath = [documentsDir stringByAppendingPathComponent:folder];
    NSString *prop = @"-Duser.home=";
    NSString *userhomeProp = [prop stringByAppendingString: folderPath];

    if (!folderPath) {
        NSLog(@"Error getting the private storage path");
    }

    NSFileManager *manager = [NSFileManager defaultManager];
    [manager createDirectoryAtPath: folderPath withIntermediateDirectories: NO attributes: nil error: nil];

    NSLog(@"Done creating private storage %@", folderPath);
    const char *userHome = [userhomeProp UTF8String];
    startGVM(userHome);
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    gvmlog(@"UIApplication launched!");
    [self performSelectorInBackground:@selector(startVM:) withObject:NULL];
    gvmlog(@"UIApplication started GVM in a separate thread");
    return YES;
}


- (void)applicationWillResignActive:(UIApplication *)application {
    gvmlog(@"[UIAPP] applicationWillResignActive");
}


- (void)applicationDidEnterBackground:(UIApplication *)application {
    gvmlog(@"[UIAPP] applicationDidEnterBackground");
}


- (void)applicationWillEnterForeground:(UIApplication *)application {
    gvmlog(@"[UIAPP] applicationWillEnterForeground");
}


- (void)applicationDidBecomeActive:(UIApplication *)application {
    gvmlog(@"[UIAPP] applicationDidBecomeActive");
}


- (void)applicationWillTerminate:(UIApplication *)application {
    gvmlog(@"[UIAPP] applicationWillTerminate");
}


@end


int startGVM(const char* userHome) {
    gvmlog(@"Starting GVM for ios");


const char* args[] = {"myapp",
          "-Dcom.sun.javafx.isEmbedded=true",
          "-Djavafx.platform=ios", userHome};
    (*run_main)(4, args);

    gvmlog(@"Finished running GVM, done with isolatehread");
    return 0;
}

void determineCPUFeatures()
{
    fprintf(stderr, "\n\n\ndetermineCpuFeaures\n");
}
