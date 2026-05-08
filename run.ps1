$JFX="D:\openjfx-26.0.1_windows-x64_bin-sdk\javafx-sdk-26.0.1\lib"
$OutDir="out"
if (Test-Path $OutDir) {
    Remove-Item $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutDir | Out-Null

javac --module-path $JFX --add-modules javafx.controls -d $OutDir (Get-ChildItem -Recurse -Filter *.java).FullName
java --module-path $JFX --add-modules javafx.controls -cp $OutDir Main
