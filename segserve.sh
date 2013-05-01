#!/bin/sh

usage() {
  echo "Usage: $0 [ctb|pku] encoding kBest" >&2
  echo "  ctb : use Chinese Treebank segmentation" >&2
  echo "  pku : Beijing University segmentation" >&2
  echo "  kBest: print kBest best segmenations; 0 means kBest mode is off." >&2
  echo >&2
  echo "Example: $0 ctb UTF-8 0" >&2
  echo "Example: $0 pku UTF-8 0" >&2
  exit
}

if [ $# -lt 3 -o $# -gt 4 ]; then
	usage
fi

ARGS="-keepAllWhitespaces false"
if [ $# -eq 4 -a "$1"=="-k" ]; then
		ARGS="-keepAllWhitespaces true"
		lang=$2
		file=$3
		enc=$4
		kBest=$5
else 
	if [ $# -eq 3 ]; then
		lang=$1
		file=$2
		enc=$3
		kBest=$4
	else
    echo $#
		usage	
	fi
fi

if [ $lang = "ctb" ]; then
    echo "(CTB):" >&2
elif [ $lang = "pku" ]; then
    echo "(PKU):" >&2
else
    echo "First argument should be either ctb or pku. Abort"
    exit
fi

echo -n "File: " >&2
echo $file >&2
echo -n "Encoding: " >&2
echo $enc >&2
echo "-------------------------------" >&2

BASEDIR=`dirname $0`
DATADIR=$BASEDIR/data
#LEXDIR=$DATADIR/lexicons
JAVACMD="java -mx2g -cp $BASEDIR/seg.jar edu.stanford.nlp.ie.NERServer -testFile $file -outputFormat xml -preserveSpacing true -port $CHINESE_SEGMENTER_PORT $ARGS"
DICTS=$DATADIR/dict-chris6.ser.gz
echo $DICTS
KBESTCMD=""

#if [ $kBest != "0" ]; then
#    KBESTCMD="-kBest $kBest"
#fi

if [ $lang = "ctb" ]; then
		$JAVACMD -loadClassifier $DATADIR/ctb.gz -serDictionary $DICTS $KBESTCMD
elif [ $lang = "pku" ]; then
		$JAVACMD -loadClassifier $DATADIR/pku.gz -serDictionary $DICTS $KBESTCMD
fi

#I really should let the user of the script set the port but...
