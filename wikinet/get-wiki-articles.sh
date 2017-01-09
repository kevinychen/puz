#!/bin/bash

PARTS=( \ zeroindexed
    p100000010p000030302 p000030304p000088444 p000088445p000200507 \
    p000200511p000352689 p000352690p000565312 p000565314p000892912 \
    p000892914p001268691 p001268693p001791079 p001791081p002336422 \
    p002336425p003046511 p003046517p003926861 p003926864p005040435 \
    p005040438p006197593 p006197599p007744799 p007744803p009518046 \
    p009518059p011539266 p011539268p013693066 p013693075p016120541 \
    p016120548p018754723 p018754736p021222156 p021222161p023927980 \
    p023927984p026823658 p026823661p030503448 p030503454p033952815 \
    p033952817p038067198 p038067204p042663461 p042663464p052158770 \
    )

mkdir -p data/
for i in {1..27}
do
    echo "Processing part $i"
    curl "https://dumps.wikimedia.org/enwiki/20161101/enwiki-20161101-pages-articles$i.xml-${PARTS[$i]}.bz2" -o data/articles$i.bz2
    bunzip2 data/articles$i.bz2
    python create_net.py < data/articles$i > data/net$i
done
python process_nets.py

