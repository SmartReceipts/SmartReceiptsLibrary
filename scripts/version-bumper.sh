#!/usr/bin/env bash

# Get full version name from ../app/build.gradle
fullVersion=`grep "versionName" ../app/build.gradle | tr -d " " | tr -d "\"" | tr -d "versionName"`
printf "\n"
echo "OLD VERSION NUMBER: $fullVersion"

# Bumping versionCode (and editing ../app/build.gradle accordingly)
printf "\n"
echo "---- Bumping versionCode & Editing it in ../app/build.gradle----"
printf "\n"

# Find versionCode in ../app/build.gradle
versionCode=`grep "versionCode" ../app/build.gradle | tr -dc '0-9'`
echo "Old version code: $versionCode"

# Bump!
bumpedVersionCode=$((4000 + $SR_BUILD_NUMBER))
echo "New version code: $bumpedVersionCode"

# Replace versionCode in ../app/build.gradle
echo "Replacing...."
sed -E "s/versionCode ([0-9]+)/versionCode $bumpedVersionCode/" ../app/build.gradle > ../app/build.gradle.temp
mv ../app/build.gradle.temp ../app/build.gradle
echo "Done!"
printf "\n"

echo "---- Editing versionName in ../app/build.gradle with new versionCode ----"
printf "\n"

# Edit the bumped versionCode into the versionName
bumpedVersionName=`echo $fullVersion | sed "s/\.$versionCode/\.$bumpedVersionCode"/`
echo "Editing versionCode in versionName in ../app/build.gradle, versionName is now: $bumpedVersionName"
printf "\n"

# Edit versionName with new versionCode and minor
echo "---- Replacing VersionName With Bumped VersionCode & Bumped Minor ----"
printf "\n"
echo "Replacing..."
sed "s/versionName \"$fullVersion\"/versionName \"$bumpedVersionName\"/" ../app/build.gradle > ../app/build.gradle.temp
mv ../app/build.gradle.temp ../app/build.gradle
echo "Done!"
printf "\n"

# Make sure the bumping worked
echo "---- Checking Final Result ----"
printf "\n"
fullVersion=`grep "versionName" ../app/build.gradle | tr -d " " | tr -d "\"" | tr -d "versionName"`
if [ "$bumpedVersionName" = "$fullVersion" ]; then
  echo "BUMPING SUCCEEDED!"
else
	echo "BUMPING FAILED!"
fi
echo "Required: $bumpedVersionName | Actual: $fullVersion"