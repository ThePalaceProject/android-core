package org.nypl.simplified.adobe.extensions;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.provider.Settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;
import com.io7m.junreachable.UnreachableCodeException;

import org.joda.time.Instant;
import org.librarysimplified.adobe.extensions.BuildConfig;
import org.nypl.drm.core.AdobeAdeptConnectorFactory;
import org.nypl.drm.core.AdobeAdeptConnectorFactoryType;
import org.nypl.drm.core.AdobeAdeptConnectorParameters;
import org.nypl.drm.core.AdobeAdeptContentFilterFactory;
import org.nypl.drm.core.AdobeAdeptContentFilterFactoryType;
import org.nypl.drm.core.AdobeAdeptContentFilterType;
import org.nypl.drm.core.AdobeAdeptExecutor;
import org.nypl.drm.core.AdobeAdeptExecutorType;
import org.nypl.drm.core.AdobeAdeptNetProvider;
import org.nypl.drm.core.AdobeAdeptNetProviderType;
import org.nypl.drm.core.AdobeAdeptResourceProvider;
import org.nypl.drm.core.AdobeAdeptResourceProviderType;
import org.nypl.drm.core.DRMException;
import org.nypl.drm.core.DRMUnsupportedException;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.json.core.JSONParserUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Functions to initialize and control Adobe DRM.
 */

public final class AdobeDRMServices {
  private static final Logger LOG = LoggerFactory.getLogger(AdobeDRMServices.class);

  private AdobeDRMServices() {
    throw new UnreachableCodeException();
  }

  /**
   * @return A serial number unique to this device.
   */

  public static String getDeviceSerial() {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(Build.SERIAL.getBytes());
      md.update(Settings.Secure.ANDROID_ID.getBytes());

      final byte[] digest = md.digest();
      final StringBuilder sb = new StringBuilder();
      for (int index = 0; index < digest.length; ++index) {
        sb.append(String.format("%02x", Byte.valueOf(digest[index])));
      }

      final String id = sb.toString();
      AdobeDRMServices.LOG.debug("device id: {}", id);
      return id;
    } catch (final NoSuchAlgorithmException e) {
      throw new UnimplementedCodeException(e);
    }
  }

  /**
   * Attempt to load an Adobe DRM content filter implementation.
   * <p>
   * The implementation checks the certificate for compatibility with the
   * package name given in the current Android manifest. However, the package
   * name can be overridden by passing {@code Some(p)} for {@code
   * package_name_opt}, if required. This is primarily useful for sharing a
   * certificate across differently branded versions of the same application
   * (with different package IDs) during development.
   *
   * @param context Application context
   * @return A DRM content filter
   * @throws DRMException If DRM is unavailable or cannot be initialized.
   */

  public static AdobeAdeptContentFilterType newAdobeContentFilter(
    final Context context,
    final AdobeConfigurationServiceType configuration)
    throws DRMException, IOException {
    NullCheck.notNull(context);
    NullCheck.notNull(configuration);

    final Logger log = AdobeDRMServices.LOG;

    final String device_name = String.format("%s/%s", Build.MANUFACTURER, Build.MODEL);
    final String device_serial = AdobeDRMServices.getDeviceSerial();
    log.debug("adobe device name:            {}", device_name);
    log.debug("adobe device serial:          {}", device_serial);

    final File baseDir =
      context.getFilesDir();
    final File baseDirVersioned =
      new File(baseDir, configuration.getDataDirectoryName());
    final File baseDirVersionedAdobe =
      new File(baseDirVersioned, "adobe");
    final File app_storage =
      new File(baseDirVersionedAdobe, "app");
    final File xml_storage =
      new File(baseDirVersionedAdobe, "xml");
    final File book_storage =
      new File(baseDirVersionedAdobe, "books-tmp");
    final File temp_storage =
      new File(baseDirVersionedAdobe, "tmp");

    DirectoryUtilities.directoryCreate(baseDir);
    DirectoryUtilities.directoryCreate(baseDirVersioned);
    DirectoryUtilities.directoryCreate(baseDirVersionedAdobe);

    log.debug("adobe app storage:            {}", app_storage);
    log.debug("adobe xml storage:            {}", xml_storage);
    log.debug("adobe temporary book storage: {}", book_storage);
    log.debug("adobe temporary storage:      {}", temp_storage);

    final String package_name;
    final String package_version;
    try {
      final PackageManager pm = context.getPackageManager();
      final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
      if (configuration.packageOverrideOption().isSome()) {
        final Some<String> package_name_some = (Some<String>) configuration.packageOverrideOption();
        package_name = package_name_some.get();
      } else {
        package_name = pi.packageName;
      }
      package_version = BuildConfig.SIMPLIFIED_VERSION;
    } catch (final PackageManager.NameNotFoundException e) {
      throw new UnreachableCodeException(e);
    }

    log.debug("package name:                 {}", package_name);
    log.debug("package version:              {}", package_version);
    final String agent = String.format("%s/%s", package_name, package_version);
    log.debug("adobe user agent:             {}", agent);

    final AdobeAdeptContentFilterFactoryType factory =
      AdobeAdeptContentFilterFactory.get();
    final byte[] certificate =
      AdobeDRMServices.getCertificateAsset(context.getAssets(), package_name);
    final AdobeAdeptResourceProviderType res = AdobeAdeptResourceProvider.get(
      certificate);
    final AdobeAdeptNetProviderType net = AdobeAdeptNetProvider.get(agent);

    return factory.get(
      package_name,
      package_version,
      res,
      net,
      device_serial,
      device_name,
      app_storage,
      xml_storage,
      book_storage,
      temp_storage);
  }

  /**
   * Attempt to load an Adobe DRM implementation.
   * <p>
   * The implementation checks the certificate for compatibility with the
   * package name given in the current Android manifest. However, the package
   * name can be overridden by passing {@code Some(p)} for {@code
   * package_name_opt}, if required. This is primarily useful for sharing a
   * certificate across differently branded versions of the same application
   * (with different package IDs) during development.
   *
   * @param context Application context
   * @return A DRM implementation
   * @throws DRMException If DRM is unavailable or cannot be initialized.
   */

  public static AdobeAdeptExecutorType newAdobeDRM(
    final Context context,
    final AdobeConfigurationServiceType configuration)
    throws DRMException, IOException {
    NullCheck.notNull(context);
    NullCheck.notNull(configuration);

    final Logger log = AdobeDRMServices.LOG;

    final String device_name = String.format("%s/%s", Build.MANUFACTURER, Build.MODEL);
    final String device_serial = AdobeDRMServices.getDeviceSerial();
    log.debug("adobe device name:            {}", device_name);
    log.debug("adobe device serial:          {}", device_serial);

    final File baseDir =
      context.getFilesDir();
    final File baseDirVersioned =
      new File(baseDir, configuration.getDataDirectoryName());
    final File baseDirVersionedAdobe =
      new File(baseDirVersioned, "adobe");
    final File app_storage =
      new File(baseDirVersionedAdobe, "app");
    final File xml_storage =
      new File(baseDirVersionedAdobe, "xml");
    final File book_storage =
      new File(baseDirVersionedAdobe, "books-tmp");
    final File temp_storage =
      new File(baseDirVersionedAdobe, "tmp");

    DirectoryUtilities.directoryCreate(baseDir);
    DirectoryUtilities.directoryCreate(baseDirVersioned);
    DirectoryUtilities.directoryCreate(baseDirVersionedAdobe);

    log.debug("adobe app storage:            {}", app_storage);
    log.debug("adobe xml storage:            {}", xml_storage);
    log.debug("adobe temporary book storage: {}", book_storage);
    log.debug("adobe temporary storage:      {}", temp_storage);

    final String package_name;
    final String package_version;
    try {
      final PackageManager pm = context.getPackageManager();
      final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
      if (configuration.packageOverrideOption().isSome()) {
        final Some<String> package_name_some = (Some<String>) configuration.packageOverrideOption();
        package_name = package_name_some.get();
      } else {
        package_name = pi.packageName;
      }
      package_version = BuildConfig.SIMPLIFIED_VERSION;
    } catch (final PackageManager.NameNotFoundException e) {
      throw new UnreachableCodeException(e);
    }

    log.debug("package name:                 {}", package_name);
    log.debug("package version:              {}", package_version);
    final String agent = String.format("%s/%s", package_name, package_version);
    log.debug("adobe user agent:             {}", agent);

    final AdobeAdeptConnectorFactoryType factory =
      AdobeAdeptConnectorFactory.get();
    final byte[] certificate =
      AdobeDRMServices.getCertificateAsset(context.getAssets(), package_name);
    final AdobeAdeptResourceProviderType res = AdobeAdeptResourceProvider.get(
      certificate);
    final AdobeAdeptNetProviderType net = AdobeAdeptNetProvider.get(agent);

    try {
      final AdobeAdeptConnectorParameters p =
        new AdobeAdeptConnectorParameters(
          package_name,
          package_version,
          res,
          net,
          device_serial,
          device_name,
          app_storage,
          xml_storage,
          book_storage,
          temp_storage,
          configuration.getDebugLogging());
      return AdobeAdeptExecutor.newExecutor(factory, p);
    } catch (final InterruptedException e) {
      throw new UnreachableCodeException();
    }
  }

  /**
   * Attempt to load an Adobe DRM implementation.
   * <p>
   * The implementation checks the certificate for compatibility with the
   * package name given in the current Android manifest. However, the package
   * name can be overridden by passing {@code Some(p)} for {@code
   * package_name_opt}, if required. This is primarily useful for sharing a
   * certificate across differently branded versions of the same application
   * (with different package IDs) during development.
   *
   * @param context Application context
   * @return A DRM implementation, if any are available
   */

  public static OptionType<AdobeAdeptExecutorType> newAdobeDRMOptional(
    final Context context,
    final AdobeConfigurationServiceType configuration) {
    try {
      return Option.some(AdobeDRMServices.newAdobeDRM(context, configuration));
    } catch (final DRMException | IOException e) {
      AdobeDRMServices.LOG.error("DRM is not supported: ", e);
      return Option.none();
    }
  }

  /**
   * Attempt to load an Adobe DRM implementation.
   * <p>
   * The implementation checks the certificate for compatibility with the
   * package name given in the current Android manifest. However, the package
   * name can be overridden by passing {@code Some(p)} for {@code
   * package_name_opt}, if required. This is primarily useful for sharing a
   * certificate across differently branded versions of the same application
   * (with different package IDs) during development.
   *
   * @param context Application context
   * @return A DRM implementation, if any are available
   */

  public static AdobeAdeptExecutorType newAdobeDRMOrNull(
    final Context context,
    final AdobeConfigurationServiceType configuration) {
    try {
      return AdobeDRMServices.newAdobeDRM(context, configuration);
    } catch (final DRMException | IOException e) {
      AdobeDRMServices.LOG.error("DRM is not supported: ", e);
      return null;
    }
  }

  /**
   * Read the certificate from the Android assets.
   *
   * @param assets       The assets
   * @param package_name The current package name
   * @return A certificate
   * @throws DRMUnsupportedException If the certificate is missing,
   *                                 inaccessible, or does not appear to be
   *                                 compatible with the current application
   */

  private static byte[] getCertificateAsset(
    final AssetManager assets,
    final String package_name)
    throws DRMUnsupportedException {
    return AdobeDRMServices.checkCertificateValidity(
      package_name, AdobeDRMServices.readCertificateBytes(assets));
  }

  /**
   * Check to see if the given certificate is usable for {@code package_name}.
   *
   * @param package_name The package name
   * @param r            The certificate bytes
   * @return {@code r}, if valid
   * @throws DRMUnsupportedException If the certificate is not valid
   */

  private static byte[] checkCertificateValidity(
    final String package_name,
    final byte[] r)
    throws DRMUnsupportedException {
    try {
      final ObjectMapper jom = new ObjectMapper();
      final JsonNode json = jom.readTree(r);
      final ObjectNode o = JSONParserUtilities.checkObject(null, json);

      final Integer expiration = JSONParserUtilities.getIntegerOrNull(o, "expireson");
      if (expiration != null) {
        final Instant timeNow = Instant.now();
        final Instant timeExpires = Instant.ofEpochSecond(expiration.longValue());
        if (timeNow.isAfter(timeExpires)) {
          final StringBuilder sb = new StringBuilder(256);
          sb.append("The included Adobe certificate has expired.\n");
          sb.append("  Expired: ");
          sb.append(timeExpires);
          sb.append(" (");
          sb.append(expiration);
          sb.append(")\n");
          throw new DRMUnsupportedException(sb.toString());
        }
      }

      final String appid = JSONParserUtilities.getString(o, "appid");
      if (appid.equals(package_name)) {
        return r;
      }
      if (("dev." + package_name).equals(appid)) {
        return r;
      }

      final StringBuilder sb = new StringBuilder(256);
      sb.append("A certificate has been provided with the wrong application ID.\n");
      sb.append("  Expected: Either ");
      sb.append(package_name);
      sb.append(" or dev.");
      sb.append(package_name);
      sb.append("\n");
      sb.append("  Got: ");
      sb.append(appid);
      throw new DRMUnsupportedException(sb.toString());
    } catch (final JsonProcessingException e) {
      throw new DRMUnsupportedException(e);
    } catch (final IOException e) {
      throw new DRMUnsupportedException(e);
    }
  }

  private static byte[] readCertificateBytes(final AssetManager assets)
    throws DRMUnsupportedException {
    try {
      final InputStream is = assets.open("ReaderClientCert.sig");
      try {
        final ByteArrayOutputStream bao = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        while (true) {
          final int r = is.read(buffer);
          if (r == -1) {
            break;
          }
          bao.write(buffer, 0, r);
        }
        return bao.toByteArray();
      } finally {
        is.close();
      }
    } catch (final IOException e) {
      throw new DRMUnsupportedException(
        "ReaderClientCert.sig is unavailable", e);
    }
  }

  /**
   * Determine if Adobe DRM support was intended to be enabled.
   *
   * @param context The application context
   * @return {@code true} if Adobe DRM was supposed to be present
   */

  public static boolean isIntendedToBePresent(Context context) {
    try {
      readCertificateBytes(context.getAssets());
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
