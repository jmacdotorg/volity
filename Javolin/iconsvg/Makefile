# This Makefile depends on SVGTOPNG being set to a command-line renderer.
# You can make one by copying this script into a text file: 

#   #!/bin/csh -f
#   java -jar "$HOME/lib/java/batik/batik-1.6/batik-rasterizer.jar" $*

SVGTOPNG = svgtopng

all: table_toolbar_buttons roster_treeicons seating_treeicons

table_toolbar_buttons: Ready_ButIcon.png Unready_ButIcon.png Seat_ButIcon.png Unseat_ButIcon.png

roster_treeicons: Avail_TreeIcon.png Unavail_TreeIcon.png Reverse_TreeIcon.png Busy_TreeIcon.png VolAvail_TreeIcon.png VolBusy_TreeIcon.png ParAvail_TreeIcon.png

seating_treeicons: Standing_TreeIcon.png Seated_TreeIcon.png Ready_TreeIcon.png Self_TreeIcon.png

coloricons: Green_0_ColorIcon.png Green_1_ColorIcon.png Green_2_ColorIcon.png Green_3_ColorIcon.png Green_4_ColorIcon.png

Ready_ButIcon.png: ready-button.svg
	$(SVGTOPNG) ready-button.svg -a 0,0,100,100 -w 20 -h 20 -d Ready_ButIcon.png

Unready_ButIcon.png: unready-button.svg
	$(SVGTOPNG) unready-button.svg -a 0,0,100,100 -w 20 -h 20 -d Unready_ButIcon.png

Seat_ButIcon.png: seat-button.svg
	$(SVGTOPNG) seat-button.svg -a 0,0,100,100 -w 20 -h 20 -d Seat_ButIcon.png

Unseat_ButIcon.png: unseat-button.svg
	$(SVGTOPNG) unseat-button.svg -a 0,0,100,100 -w 20 -h 20 -d Unseat_ButIcon.png

Blank_TreeIcon.png: blank.svg
	$(SVGTOPNG) blank.svg -a 0,0,100,100 -w 1 -h 16 -d Blank_TreeIcon.png

Avail_TreeIcon.png: avail-treeicon.svg
	$(SVGTOPNG) avail-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d Avail_TreeIcon.png

Unavail_TreeIcon.png: unavail-treeicon.svg
	$(SVGTOPNG) unavail-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d Unavail_TreeIcon.png

Reverse_TreeIcon.png: reverse-treeicon.svg
	$(SVGTOPNG) reverse-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d Reverse_TreeIcon.png

Busy_TreeIcon.png: busy-treeicon.svg
	$(SVGTOPNG) busy-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d Busy_TreeIcon.png

VolAvail_TreeIcon.png: volavail-treeicon.svg
	$(SVGTOPNG) volavail-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d VolAvail_TreeIcon.png

ParAvail_TreeIcon.png: paravail-treeicon.svg
	$(SVGTOPNG) paravail-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d ParAvail_TreeIcon.png

VolBusy_TreeIcon.png: volbusy-treeicon.svg
	$(SVGTOPNG) volbusy-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d VolBusy_TreeIcon.png

Standing_TreeIcon.png: standing-treeicon.svg
	$(SVGTOPNG) standing-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d Standing_TreeIcon.png

Seated_TreeIcon.png: seated-treeicon.svg
	$(SVGTOPNG) seated-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d Seated_TreeIcon.png

Ready_TreeIcon.png: ready-treeicon.svg
	$(SVGTOPNG) ready-treeicon.svg -a 0,0,100,100 -w 16 -h 16 -d Ready_TreeIcon.png

Self_TreeIcon.png: self-treeicon.svg
	$(SVGTOPNG) self-treeicon.svg -a 0,0,100,100 -w 12 -h 12 -d Self_TreeIcon.png

Green_0_ColorIcon.png: green-0-coloricon.svg
	$(SVGTOPNG) green-0-coloricon.svg -a 0,0,100,100 -w 16 -h 16 -d Green_0_ColorIcon.png

Green_1_ColorIcon.png: green-1-coloricon.svg
	$(SVGTOPNG) green-1-coloricon.svg -a 0,0,100,100 -w 16 -h 16 -d Green_1_ColorIcon.png

Green_2_ColorIcon.png: green-2-coloricon.svg
	$(SVGTOPNG) green-2-coloricon.svg -a 0,0,100,100 -w 16 -h 16 -d Green_2_ColorIcon.png

Green_3_ColorIcon.png: green-3-coloricon.svg
	$(SVGTOPNG) green-3-coloricon.svg -a 0,0,100,100 -w 16 -h 16 -d Green_3_ColorIcon.png

Green_4_ColorIcon.png: green-4-coloricon.svg
	$(SVGTOPNG) green-4-coloricon.svg -a 0,0,100,100 -w 16 -h 16 -d Green_4_ColorIcon.png


vollogo-200.png: vollogo.svg
	$(SVGTOPNG) vollogo.svg -a 0,0,200,200 -w 200 -h 200
	pngtopnm -mix vollogo.png | ppmquant -meanpixel 253 | pnmtopng > vollogo-200.png

clean:
	rm -f *~ *.png
