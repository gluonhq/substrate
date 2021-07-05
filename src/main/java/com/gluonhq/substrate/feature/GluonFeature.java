package com.gluonhq.substrate.feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.jni.hosted.JNIFeature;
import java.util.Collections;
import java.util.List;
import org.graalvm.nativeimage.hosted.Feature;


/**
 *
 * @author johan
 */
@AutomaticFeature
public class GluonFeature implements Feature {
  @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        System.err.println("GLUONFEATURE isInConfig?");
        return true;
    }

//    @Override
//    public List<Class<? extends Feature>> getRequiredFeatures() {
//        return Collections.singletonList(JNIFeature.class);
//    }
//    
    
    public void duringSetup(DuringSetupAccess access) {
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("prism_es2");
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("glass");
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("glassgtk3");
        System.err.println("GLUON FEATURE, during setup done!");

    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        System.err.println("GLUON FEATURE, before analysis!");
        NativeLibraries nativeLibraries = ((BeforeAnalysisAccessImpl) a).getNativeLibraries();
        nativeLibraries.addStaticJniLibrary("prism_es2");
        nativeLibraries.addStaticJniLibrary("glass");
        nativeLibraries.addStaticJniLibrary("glassgtk3");
        
        PlatformNativeLibrarySupport pnls = PlatformNativeLibrarySupport.singleton();
        pnls.addBuiltinPkgNativePrefix("com_sun_javafx_iio_jpeg");
        pnls.addBuiltinPkgNativePrefix("com_sun_javafx_font_FontConfigManager");
        pnls.addBuiltinPkgNativePrefix("com_sun_javafx_font_freetype");
     //   pnls.addBuiltinPkgNativePrefix("com_sun_javafx_font_PrismFontFactory");
        pnls.addBuiltinPkgNativePrefix("com_sun_prism");
        pnls.addBuiltinPkgNativePrefix("com_sun_glass");
        System.err.println("GLUON FEATURE, before analysis, nl = "+nativeLibraries.getJniStaticLibraries());
    }
//
//    @Override
//    public void duringAnalysis(DuringAnalysisAccess access) {
//        NativeLibraries nativeLibraries = ((DuringAnalysisAccessImpl) a).getNativeLibraries();
//        NativeLibraries nativeLibraries = CEntryPointCallStubSupport.singleton().getNativeLibraries();
//        List<String> staticLibNames = nativeLibraries.getJniStaticLibraries();
//        boolean isChanged = jniLibraryInitializer.fillCGlobalDataMap(staticLibNames);
//        if (isChanged) {
//            access.requireAnalysisIteration();
//        }
//    }
}
