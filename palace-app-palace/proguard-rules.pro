-dontobfuscate

-dontwarn javax.xml.transform.stax.StAXSource
-dontwarn javax.xml.transform.stax.StAXResult
-dontwarn com.sun.activation.registries.LogSupport
-dontwarn com.sun.activation.registries.MailcapFile
-dontwarn java.awt.datatransfer.DataFlavor
-dontwarn java.awt.datatransfer.Transferable
-dontwarn java.sql.JDBCType
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.io7m.immutables.styles.ImmutablesStyleType
-dontwarn org.immutables.value.Value$Default
-dontwarn org.immutables.value.Value$Immutable
-dontwarn org.immutables.value.Value$Modifiable
-dontwarn org.immutables.value.Value$Parameter
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
-dontwarn org.osgi.annotation.versioning.ConsumerType
-dontwarn org.osgi.annotation.versioning.ProviderType
-dontwarn org.apache.xml.resolver.**
-dontwarn org.w3c.dom.ElementTraversal
-dontwarn org.w3c.dom.events.*
-dontwarn org.w3c.dom.ls.*
-dontwarn org.w3c.dom.ranges.*
-dontwarn org.w3c.dom.traversal.*
-dontwarn org.w3c.dom.views.*
-dontwarn com.google.android.datatransport.ProductData
-dontwarn org.readium.sdk.android.**

-keep class ch.qos.logback.classic.pattern.*Converter {
    <init>();
}

-keep class ch.qos.logback.core.rolling.helper.*Converter {
    <init>();
}

-keep class org.librarysimplified.viewer.pdf.pdfjs.PdfServer$* {
    <init>();
}

-keepattributes Signature, InnerClasses, EnclosingMethod

-keep class io.audioengine.mobile.** {
    *;
}

# Retrofit under R8 full mode.
#
# In R8 full mode (the default in current AGP) generic signatures are
# stripped from classes that are not kept. Retrofit resolves service method
# return types reflectively (Method.getGenericReturnType), so once
# rx.Observable, retrofit2.Response, and kotlin.coroutines.Continuation lose
# their signatures the generated proxy throws
#   ClassCastException: java.lang.Class cannot be cast to
#       java.lang.reflect.ParameterizedType
#   IllegalArgumentException: Unable to create call adapter for class rx.Observable
# (observed on io.audioengine.mobile.AudioEngineService: getPlaylist, content).
#
# The consumer rules bundled with retrofit 2.6.4 predate these fixes, and
# 2.6.4 cannot be upgraded, so the rules Retrofit added upstream are
# duplicated here:
#   https://github.com/square/retrofit/issues/3751  (same ClassCastException at HttpServiceMethod.parseAnnotations:46)
#   https://github.com/square/retrofit/issues/3774  (crash with R8 full mode, generics and Rx)
#   https://github.com/square/retrofit/pull/3886    (keep rule for the generic signature of return types, merged 2023-05-05)

# Keep the generic signature of each service method's return type (<3> is
# the return type of the matched method), e.g. rx.Observable.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Suspend functions: the real return type is the type argument of Continuation.
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation

# retrofit2.Response<T> occurs as a type argument of service methods.
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response

# Keep inherited service interfaces (services extending other services).
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

-keep class org.nypl.drm.** {
    *;
}

-keep class com.adobe.** {
    *;
}

-keep class org.readium.sdk.android.** {
    *;
}

# The liblcp native client is used via string-based reflection
# (Class.forName("org.readium.lcp.sdk.Lcp") followed by
# getDeclaredConstructor().newInstance(), in readium-lcp's
# LcpClient.isAvailable). R8 cannot track such references and strips all
# members of the class, so getDeclaredConstructor() raises
# NoSuchMethodException, isAvailable() reports false, and LCP support is
# silently disabled. The liblcp AAR ships no consumer rules.
-keep class org.readium.lcp.sdk.** {
    *;
}

-keep class org.apache.xerces.** {
    *;
}

-keep class org.sqlite.** {
    *;
}

-keep class org.librarysimplified.http.** {
    *;
}
