plugins {
    application
}

dependencies {
    implementation(project(":clinic-core"))
}

application {
    mainClass.set("com.clinic.ClinicApplication")
}
