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

# RxJava 1.x (io.reactivex:rxjava) internal queues.
#
# The queues in rx.internal.util.unsafe resolve their field offsets at
# class-init time via reflection by name, e.g.
#   SpscArrayQueueProducerFields.<clinit>:
#     P_INDEX_OFFSET = UnsafeAccess.addressOf(
#         SpscArrayQueueProducerFields.class, "producerIndex")
# which does Class.getDeclaredField("producerIndex").
#
# In R8 full mode the direct field read in SpscArrayQueue.offer()
# (`final long index = producerIndex;`) is rewritten to a
# sun.misc.Unsafe offset load (the field is already written that way via
# soProducerIndex), so the field itself is removed. The reflective
# getDeclaredField then fails when the class is first initialized, throwing
#   java.lang.InternalError
#     Caused by: java.lang.NoSuchFieldException: No field producerIndex
#         in class Lrx/internal/util/unsafe/SpscArrayQueueProducerFields;
# on the first use of any SpscArrayQueue-backed operator (observeOn, via
# sqlbrite BriteDatabase) - i.e. opening an audiobook.
#
# These are the ProGuard rules documented for RxJava 1.x; they pin the
# reflectively-resolved field names so R8 neither renames nor removes them.
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

# WebPub core model classes are (de)serialized reflectively by Jackson
# (WPMManifest / WPMMetadata via WPMMappers.createMapper()).
#
# In R8 full mode the generic `Signature` attribute is stripped from classes
# not matched by a -keep rule, even when `-keepattributes Signature` is set
# (see https://developer.android.com/topic/performance/app-optimization/full-mode
# and r8 compatibility-faq). Once a `List<X>` field loses its signature Jackson
# erases it to `List<Object>`. The mapper installs dixmont's
# DmJsonRestrictedDeserializers (com.io7m.dixmont.core), whose
# findCollectionDeserializer checks CollectionType.toCanonical() against a
# whitelist of concrete forms (e.g. `java.util.List<org.thepalaceproject.webpub.core.WPMContributorOrString>`
# built in WPMModule); the erased `java.util.List<java.lang.Object>` is not in
# that set, so it throws
#   "Deserializing a value of type java.util.List<java.lang.Object> is not allowed"
# when AccountProviderRegistry2.fetchRegistryPage reads the registry page.
#
# Keeping the package preserves the field/parameter generic signatures (and the
# fields themselves, which R8 would otherwise shrink as reflectively-only), so
# Jackson resolves the real element types. No webpub.core class is excluded.
-keep class org.thepalaceproject.webpub.core.** {
    *;
}

# protobuf-javalite (4.x) parses and serializes generated lite messages with a
# reflection-based runtime: GeneratedMessageLite.parsePartialFrom() builds a
# MessageSchema for the message class, and MessageSchema.reflectField() resolves
# every descriptor field by name via Class.getDeclaredField("<name>_") (then
# reads/writes the backing field directly, via sun.misc.Unsafe offsets where
# available). R8 cannot see these reflective accesses and removes fields that
# no app code references. DBSLink1.width_ was removed (linkFromP1 never calls
# getWidth()), so at startup
#   DBAccountProviderDescriptions.parseFromResult -> parseFromProto1 ->
#       descriptionFromP1Bytes -> parser.parseFrom(bytes)
# MessageSchema threw
#   java.lang.RuntimeException: Field width_ for
#       org.thepalaceproject.db.internal.generated.DBSerialization$DBSLink1 not
#       found. Known fields are [bitrate_ , duration_ , height_ , href_ ,
#       relation_ , templated_ , title_ , type_]
# (wrapping java.lang.NoSuchFieldException from Class.getDeclaredField).
#
# This is the rule documented for the lite runtime in protobuf's java/lite.md
# ("R8 rule to make production app builds work"), which documents exactly this
# exception shape.
-keep class * extends com.google.protobuf.GeneratedMessageLite {
    *;
}
