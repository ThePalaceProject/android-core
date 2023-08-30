dependencies {
  implementation(project(":simplified-announcements"))
  implementation(project(":simplified-json-core"))
  implementation(project(":simplified-links"))
  implementation(project(":simplified-links-json"))
  implementation(project(":simplified-opds-auth-document-api"))
  implementation(project(":simplified-parser-api"))

  implementation(libs.jackson.databind)
  implementation(libs.jackson.core)
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlin.stdlib)

  compileOnly(libs.google.auto.value)
  annotationProcessor(libs.google.auto.value.processor)
}
