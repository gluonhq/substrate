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
#import <Cocoa/Cocoa.h>
#include <pthread.h>

extern void *run_main(int argc, const char* argv[]);

@interface AppDelegate : NSObject <NSApplicationDelegate>
@end

@interface AppDelegate ()
@end

@implementation AppDelegate

-(void)startVM:(id)selector {
    NSLog(@"Starting Gluon VM...");
    pthread_t me = pthread_self();
    // fprintf(stderr, "Starting on thread: %p\n",me);

    // generate char** CLI arguments from NSProcessInfo
    NSArray *nsargs = [[NSProcessInfo processInfo] arguments];
    unsigned count = [nsargs count];
    const char **args = (const char **)malloc((count + 1) * sizeof(char*));
    for (unsigned int i = 0; i < count; ++i) {
        args[i] = strdup([[nsargs objectAtIndex:i] UTF8String]);
    }
    args[count] = NULL;
    (*run_main)(count, args);
    NSLog(@"Started Gluon VM...");
    free(args);
}

- (void)applicationWillFinishLaunching:(NSNotification *)aNotification  {
    pthread_t me = pthread_self();
}

- (void)applicationDidFinishLaunching:(NSNotification *)aNotification  {
    pthread_t me = pthread_self();
    // fprintf(stderr, "ApplicationDidFinishLaunching called, me = %p\n", me);
    [self performSelectorInBackground:@selector(startVM:) withObject:nil];
}

- (void)applicationDidFinishLaunchingWithOptions:(NSNotification *)aNotification  {
    // fprintf(stderr, "DIDFINISHLAUNCHING with options\n");
     [self performSelectorInBackground:@selector(startVM:) withObject:nil];
}

-(BOOL) applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)app {
    return YES;
}

@end

void outBox(int argc, const char** argv) {
    pthread_t me = pthread_self();
    // fprintf(stderr, "in outbox, on thread %p, argc = %d and argv = %p\n", me,argc, argv);

    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    AppDelegate* appDelegate = [[AppDelegate alloc] init];
    NSApplication.sharedApplication.delegate = appDelegate;
    // fprintf(stderr, "in outbox2, on thread %p\n", me);
    [NSApplication sharedApplication];

    [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];

    dispatch_async(dispatch_get_main_queue(), ^{
            [NSApp activateIgnoringOtherApps:YES];
        });

    // fprintf(stderr, "sharedall called\n");
    [NSApp run];
    // fprintf(stderr, "in outbox3, on thread %p\n", me);

    [pool release];

    // fprintf(stderr, "Outbox exit now\n");
}
