package org.nypl.simplified.tests.mocking

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Display
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class MockContext : Context() {
  override fun getAssets(): AssetManager {
    TODO("Not yet implemented")
  }

  override fun getResources(): Resources {
    TODO("Not yet implemented")
  }

  override fun getPackageManager(): PackageManager {
    TODO("Not yet implemented")
  }

  override fun getContentResolver(): ContentResolver {
    TODO("Not yet implemented")
  }

  override fun getMainLooper(): Looper {
    TODO("Not yet implemented")
  }

  override fun getApplicationContext(): Context {
    TODO("Not yet implemented")
  }

  override fun setTheme(p0: Int) {
    TODO("Not yet implemented")
  }

  override fun getTheme(): Resources.Theme {
    TODO("Not yet implemented")
  }

  override fun getClassLoader(): ClassLoader {
    TODO("Not yet implemented")
  }

  override fun getPackageName(): String {
    TODO("Not yet implemented")
  }

  override fun getApplicationInfo(): ApplicationInfo {
    TODO("Not yet implemented")
  }

  override fun getPackageResourcePath(): String {
    TODO("Not yet implemented")
  }

  override fun getPackageCodePath(): String {
    TODO("Not yet implemented")
  }

  override fun getSharedPreferences(p0: String?, p1: Int): SharedPreferences {
    TODO("Not yet implemented")
  }

  override fun moveSharedPreferencesFrom(p0: Context?, p1: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun deleteSharedPreferences(p0: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun openFileInput(p0: String?): FileInputStream {
    TODO("Not yet implemented")
  }

  override fun openFileOutput(p0: String?, p1: Int): FileOutputStream {
    TODO("Not yet implemented")
  }

  override fun deleteFile(p0: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun getFileStreamPath(p0: String?): File {
    TODO("Not yet implemented")
  }

  override fun getDataDir(): File {
    TODO("Not yet implemented")
  }

  override fun getFilesDir(): File {
    TODO("Not yet implemented")
  }

  override fun getNoBackupFilesDir(): File {
    TODO("Not yet implemented")
  }

  override fun getExternalFilesDir(p0: String?): File? {
    TODO("Not yet implemented")
  }

  override fun getExternalFilesDirs(p0: String?): Array<File> {
    TODO("Not yet implemented")
  }

  override fun getObbDir(): File {
    TODO("Not yet implemented")
  }

  override fun getObbDirs(): Array<File> {
    TODO("Not yet implemented")
  }

  override fun getCacheDir(): File {
    TODO("Not yet implemented")
  }

  override fun getCodeCacheDir(): File {
    TODO("Not yet implemented")
  }

  override fun getExternalCacheDir(): File? {
    TODO("Not yet implemented")
  }

  override fun getExternalCacheDirs(): Array<File> {
    TODO("Not yet implemented")
  }

  override fun getExternalMediaDirs(): Array<File> {
    TODO("Not yet implemented")
  }

  override fun fileList(): Array<String> {
    TODO("Not yet implemented")
  }

  override fun getDir(p0: String?, p1: Int): File {
    TODO("Not yet implemented")
  }

  override fun openOrCreateDatabase(
    p0: String?,
    p1: Int,
    p2: SQLiteDatabase.CursorFactory?
  ): SQLiteDatabase {
    TODO("Not yet implemented")
  }

  override fun openOrCreateDatabase(
    p0: String?,
    p1: Int,
    p2: SQLiteDatabase.CursorFactory?,
    p3: DatabaseErrorHandler?
  ): SQLiteDatabase {
    TODO("Not yet implemented")
  }

  override fun moveDatabaseFrom(p0: Context?, p1: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun deleteDatabase(p0: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun getDatabasePath(p0: String?): File {
    TODO("Not yet implemented")
  }

  override fun databaseList(): Array<String> {
    TODO("Not yet implemented")
  }

  override fun getWallpaper(): Drawable {
    TODO("Not yet implemented")
  }

  override fun peekWallpaper(): Drawable {
    TODO("Not yet implemented")
  }

  override fun getWallpaperDesiredMinimumWidth(): Int {
    TODO("Not yet implemented")
  }

  override fun getWallpaperDesiredMinimumHeight(): Int {
    TODO("Not yet implemented")
  }

  override fun setWallpaper(p0: Bitmap?) {
    TODO("Not yet implemented")
  }

  override fun setWallpaper(p0: InputStream?) {
    TODO("Not yet implemented")
  }

  override fun clearWallpaper() {
    TODO("Not yet implemented")
  }

  override fun startActivity(p0: Intent?) {
    TODO("Not yet implemented")
  }

  override fun startActivity(p0: Intent?, p1: Bundle?) {
    TODO("Not yet implemented")
  }

  override fun startActivities(p0: Array<out Intent>?) {
    TODO("Not yet implemented")
  }

  override fun startActivities(p0: Array<out Intent>?, p1: Bundle?) {
    TODO("Not yet implemented")
  }

  override fun startIntentSender(p0: IntentSender?, p1: Intent?, p2: Int, p3: Int, p4: Int) {
    TODO("Not yet implemented")
  }

  override fun startIntentSender(
    p0: IntentSender?,
    p1: Intent?,
    p2: Int,
    p3: Int,
    p4: Int,
    p5: Bundle?
  ) {
    TODO("Not yet implemented")
  }

  override fun sendBroadcast(p0: Intent?) {
    TODO("Not yet implemented")
  }

  override fun sendBroadcast(p0: Intent?, p1: String?) {
    TODO("Not yet implemented")
  }

  override fun sendOrderedBroadcast(p0: Intent?, p1: String?) {
    TODO("Not yet implemented")
  }

  override fun sendOrderedBroadcast(
    p0: Intent,
    p1: String?,
    p2: BroadcastReceiver?,
    p3: Handler?,
    p4: Int,
    p5: String?,
    p6: Bundle?
  ) {
    TODO("Not yet implemented")
  }

  override fun sendBroadcastAsUser(p0: Intent?, p1: UserHandle?) {
    TODO("Not yet implemented")
  }

  override fun sendBroadcastAsUser(p0: Intent?, p1: UserHandle?, p2: String?) {
    TODO("Not yet implemented")
  }

  override fun sendOrderedBroadcastAsUser(
    p0: Intent?,
    p1: UserHandle?,
    p2: String?,
    p3: BroadcastReceiver?,
    p4: Handler?,
    p5: Int,
    p6: String?,
    p7: Bundle?
  ) {
    TODO("Not yet implemented")
  }

  override fun sendStickyBroadcast(p0: Intent?) {
    TODO("Not yet implemented")
  }

  override fun sendStickyOrderedBroadcast(
    p0: Intent?,
    p1: BroadcastReceiver?,
    p2: Handler?,
    p3: Int,
    p4: String?,
    p5: Bundle?
  ) {
    TODO("Not yet implemented")
  }

  override fun removeStickyBroadcast(p0: Intent?) {
    TODO("Not yet implemented")
  }

  override fun sendStickyBroadcastAsUser(p0: Intent?, p1: UserHandle?) {
    TODO("Not yet implemented")
  }

  override fun sendStickyOrderedBroadcastAsUser(
    p0: Intent?,
    p1: UserHandle?,
    p2: BroadcastReceiver?,
    p3: Handler?,
    p4: Int,
    p5: String?,
    p6: Bundle?
  ) {
    TODO("Not yet implemented")
  }

  override fun removeStickyBroadcastAsUser(p0: Intent?, p1: UserHandle?) {
    TODO("Not yet implemented")
  }

  override fun registerReceiver(p0: BroadcastReceiver?, p1: IntentFilter?): Intent? {
    TODO("Not yet implemented")
  }

  override fun registerReceiver(p0: BroadcastReceiver?, p1: IntentFilter?, p2: Int): Intent? {
    TODO("Not yet implemented")
  }

  override fun registerReceiver(
    p0: BroadcastReceiver?,
    p1: IntentFilter?,
    p2: String?,
    p3: Handler?
  ): Intent? {
    TODO("Not yet implemented")
  }

  override fun registerReceiver(
    p0: BroadcastReceiver?,
    p1: IntentFilter?,
    p2: String?,
    p3: Handler?,
    p4: Int
  ): Intent? {
    TODO("Not yet implemented")
  }

  override fun unregisterReceiver(p0: BroadcastReceiver?) {
    TODO("Not yet implemented")
  }

  override fun startService(p0: Intent?): ComponentName? {
    TODO("Not yet implemented")
  }

  override fun startForegroundService(p0: Intent?): ComponentName? {
    TODO("Not yet implemented")
  }

  override fun stopService(p0: Intent?): Boolean {
    TODO("Not yet implemented")
  }

  override fun bindService(p0: Intent, p1: ServiceConnection, p2: Int): Boolean {
    TODO("Not yet implemented")
  }

  override fun unbindService(p0: ServiceConnection) {
    TODO("Not yet implemented")
  }

  override fun startInstrumentation(p0: ComponentName, p1: String?, p2: Bundle?): Boolean {
    TODO("Not yet implemented")
  }

  override fun getSystemService(p0: String): Any {
    TODO("Not yet implemented")
  }

  override fun getSystemServiceName(p0: Class<*>): String? {
    TODO("Not yet implemented")
  }

  override fun checkPermission(p0: String, p1: Int, p2: Int): Int {
    TODO("Not yet implemented")
  }

  override fun checkCallingPermission(p0: String): Int {
    TODO("Not yet implemented")
  }

  override fun checkCallingOrSelfPermission(p0: String): Int {
    TODO("Not yet implemented")
  }

  override fun checkSelfPermission(p0: String): Int {
    TODO("Not yet implemented")
  }

  override fun enforcePermission(p0: String, p1: Int, p2: Int, p3: String?) {
    TODO("Not yet implemented")
  }

  override fun enforceCallingPermission(p0: String, p1: String?) {
    TODO("Not yet implemented")
  }

  override fun enforceCallingOrSelfPermission(p0: String, p1: String?) {
    TODO("Not yet implemented")
  }

  override fun grantUriPermission(p0: String?, p1: Uri?, p2: Int) {
    TODO("Not yet implemented")
  }

  override fun revokeUriPermission(p0: Uri?, p1: Int) {
    TODO("Not yet implemented")
  }

  override fun revokeUriPermission(p0: String?, p1: Uri?, p2: Int) {
    TODO("Not yet implemented")
  }

  override fun checkUriPermission(p0: Uri?, p1: Int, p2: Int, p3: Int): Int {
    TODO("Not yet implemented")
  }

  override fun checkUriPermission(
    p0: Uri?,
    p1: String?,
    p2: String?,
    p3: Int,
    p4: Int,
    p5: Int
  ): Int {
    TODO("Not yet implemented")
  }

  override fun checkCallingUriPermission(p0: Uri?, p1: Int): Int {
    TODO("Not yet implemented")
  }

  override fun checkCallingOrSelfUriPermission(p0: Uri?, p1: Int): Int {
    TODO("Not yet implemented")
  }

  override fun enforceUriPermission(p0: Uri?, p1: Int, p2: Int, p3: Int, p4: String?) {
    TODO("Not yet implemented")
  }

  override fun enforceUriPermission(
    p0: Uri?,
    p1: String?,
    p2: String?,
    p3: Int,
    p4: Int,
    p5: Int,
    p6: String?
  ) {
    TODO("Not yet implemented")
  }

  override fun enforceCallingUriPermission(p0: Uri?, p1: Int, p2: String?) {
    TODO("Not yet implemented")
  }

  override fun enforceCallingOrSelfUriPermission(p0: Uri?, p1: Int, p2: String?) {
    TODO("Not yet implemented")
  }

  override fun createPackageContext(p0: String?, p1: Int): Context {
    TODO("Not yet implemented")
  }

  override fun createContextForSplit(p0: String?): Context {
    TODO("Not yet implemented")
  }

  override fun createConfigurationContext(p0: Configuration): Context {
    TODO("Not yet implemented")
  }

  override fun createDisplayContext(p0: Display): Context {
    TODO("Not yet implemented")
  }

  override fun createDeviceProtectedStorageContext(): Context {
    TODO("Not yet implemented")
  }

  override fun isDeviceProtectedStorage(): Boolean {
    TODO("Not yet implemented")
  }
}
