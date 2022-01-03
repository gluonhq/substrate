/*
 * Copyright (c) 2019, 2021, Gluon
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
#include "jni.h"

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

int startGVM(const char* userHome, const char* userTimeZone, const char* userLaunchKey);

extern int __svm_vm_is_static_binary __attribute__((weak)) = 1;

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

-(void)startVM:(NSDictionary *)launchOptions {
    gvmlog(@"Starting vm...");
    NSArray *documentPaths = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES);
    NSString *documentsDir = [documentPaths objectAtIndex:0];
    NSString *folder = @"gluon";
    NSString *folderPath = [documentsDir stringByAppendingPathComponent:folder];
    if (!folderPath) {
        NSLog(@"Could not create user.home dir");
    }
    NSString *prop = @"-Duser.home=";
    NSString *userhomeProp = [prop stringByAppendingString: folderPath];

    NSFileManager *manager = [NSFileManager defaultManager];
    [manager createDirectoryAtPath: folderPath withIntermediateDirectories: NO attributes: nil error: nil];

    NSLog(@"Done creating user.home at %@", folderPath);
    const char *userHome = [userhomeProp UTF8String];

    NSTimeZone *timeZone = [NSTimeZone localTimeZone];
    NSString *tzName = [@"-Duser.timezone=" stringByAppendingString: [timeZone name]];
    const char *userTimeZone = [tzName UTF8String];

    const char *userLaunchKey;
    // 1. Check Launch.URL
    id urlId = [launchOptions objectForKey:UIApplicationLaunchOptionsURLKey];
    if ([urlId isKindOfClass:[NSURL class]]) {
        NSURL *url = (NSURL *)urlId;
        NSString *urlName = [@"-DLaunch.URL=" stringByAppendingString: url.absoluteString];
        NSLog(@"LaunchOptions :: URL :: urlName: %@", urlName);
        userLaunchKey = [urlName UTF8String];
        // TODO: UIApplicationLaunchOptionsSourceApplicationKey gives bundleID of source app that launched this app
    } else {
        // 2. Check Launch.LocalNotification
        id notifId = [launchOptions objectForKey:@"UIApplicationLaunchOptionsLocalNotificationKey"];
        if ([notifId isKindOfClass:[UILocalNotification class]]) {
            UILocalNotification* notif = (UILocalNotification *)notifId;
            NSDictionary* userInfo = [notif userInfo];
            NSString *userIdName = [@"-DLaunch.LocalNotification=" stringByAppendingString: [userInfo objectForKey:@"userId"]];
            NSLog(@"LaunchOptions :: LocalNotification :: userId: %@", userIdName);
            userLaunchKey = [userIdName UTF8String];
        } else {
            NSDictionary* userInfo = [launchOptions objectForKey:UIApplicationLaunchOptionsRemoteNotificationKey];
            if (userInfo != nil) {
                NSString *urlName = [@"-DLaunch.PushNotification=" stringByAppendingString: [userInfo description]];
                NSLog(@"LaunchOptions :: PushNotification :: urlName: %@", urlName);
                userLaunchKey = [urlName UTF8String];
            } else {
                // TODO: Process other launch options
                NSString *empty = @"";
                userLaunchKey = [empty UTF8String];
            }
        }
    }

    startGVM(userHome, userTimeZone, userLaunchKey);
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    gvmlog(@"UIApplication launched!");
    [self performSelectorInBackground:@selector(startVM:) withObject:launchOptions];
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


int startGVM(const char* userHome, const char* userTimeZone, const char* userLaunchKey) {
    gvmlog(@"Starting GVM for ios");

    // this array is filled during compile/link phases
    const char *userArgs[] = {
    // USER_RUNTIME_ARGS
    };

    const char* args[] = {"myapp",
          "-Dcom.sun.javafx.isEmbedded=true",
          "-Djavafx.platform=ios",
          userHome, userTimeZone, userLaunchKey};

    int userArgsSize = sizeof(userArgs) / sizeof(char *);
    int argc = sizeof(args) / sizeof(char *);
    int argsSize = userArgsSize + argc;
    char **graalArgs = (char **)malloc(argsSize * sizeof(char *));
    for (int i = 0; i < argc; i++)
    {
         graalArgs[i] = (char *)args[i];
    }
    for (int i = 0; i < userArgsSize; i++)
    {
        graalArgs[argc + i] = (char *)userArgs[i];
    }
    (*run_main)(argsSize, graalArgs);
    free(graalArgs);

    gvmlog(@"Finished running GVM, done with isolatehread");
    return 0;
}

typedef struct {
#ifdef GVM_IOS_SIM
  char fCX8;
  char fCMOV;
  char fFXSR;
  char fHT;
  char fMMX;
  char fAMD3DNOWPREFETCH;
  char fSSE;
  char fSSE2;
  char fSSE3;
  char fSSSE3;
  char fSSE4A;
  char fSSE41;
  char fSSE42;
  char fPOPCNT;
  char fLZCNT;
  char fTSC;
  char fTSCINV;
  char fAVX;
  char fAVX2;
  char fAES;
  char fERMS;
  char fCLMUL;
  char fBMI1;
  char fBMI2;
  char fRTM;
  char fADX;
  char fAVX512F;
  char fAVX512DQ;
  char fAVX512PF;
  char fAVX512ER;
  char fAVX512CD;
  char fAVX512BW;
  char fAVX512VL;
  char fSHA;
  char fFMA;
#else
  char fFP;
  char fASIMD;
  char fEVTSTRM;
  char fAES;
  char fPMULL;
  char fSHA1;
  char fSHA2;
  char fCRC32;
  char fLSE;
  char fSTXRPREFETCH;
  char fA53MAC;
  char fDMBATOMICS;
#endif
} CPUFeatures;

void determineCPUFeatures(CPUFeatures* features)
{
    fprintf(stderr, "\n\n\ndetermineCpuFeaures\n");
#ifdef GVM_IOS_SIM
    features->fSSE = 1;
    features->fSSE2 = 1;
#else
    features->fFP = 1;
    features->fASIMD = 1;
#endif
}

#ifdef GVM_17
// dummy symbols only for JDK17
void Java_java_net_AbstractPlainDatagramSocketImpl_isReusePortAvailable0() {}
void Java_java_net_AbstractPlainSocketImpl_isReusePortAvailable0() {}
void Java_java_net_DatagramPacket_init() {}
#endif