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

-keep class org.nypl.drm.** {
    *;
}

-keep class com.adobe.** {
    *;
}

-keep class org.readium.sdk.android.** {
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
