root=`pwd`/`dirname $0`
cd $root

if [ ! -f $root/jdk/win/jdk-9.0.1 ] ; then 
    mkdir -p $root/jdk/win/
    ln -s /mnt/c/Program\ Files/Java/jdk-9.0.1/ $root/jdk/win/jdk-9.0.1
fi

JDK=$root/jdk/win/jdk-9.0.1/bin

echo $JDK

function join() {
    local IFS=$1
    shift
    echo "$*"
}


echo -- using javac from : --
echo `which ${JDK}/javac.exe` / `${JDK}/javac.exe -fullversion`

echo -- removing build directory --

mkdir build
cd build
mkdir linker_classes


echo

echo -- building field linker jar --
find ../linker/src -iname '*.java' > linker_source
echo -- 1/2 - compiling classes --
${JDK}/javac.exe -Xlint:-deprecation -Xlint:-unchecked -classpath "../linker/lib/*"  @linker_source -d linker_classes/
cd linker_classes
cp -r ../../linker/lib/META-INF .
echo -- 2/2 - making jar --
${JDK}/jar.exe cmf ../../linker/lib/META-INF/MANIFEST.MF ../field_linker.jar .
cd ..
echo



echo -- build complete --
