How to use:

Download your google location history here: https://takeout.google.com/takeout/custom/mymaps,local_actions,location_history?continue=https://myaccount.google.com/yourdata/maps?hl%3Den%26authuser%3D0&pli=1

Arguments:
java -jar DaysAbroad.jar -f "Records.json" -l 54.0085726,-1.4301757 -r 381 -t -v

-f <file path>: Required. Days spent outside of UK. File path must point to google location history takeout file in json format. (See https://takeout.google.com/settings/takeout)
-s <dd/mm/yyyy>: Optional. UTC start date. If not specified the start date from the file is used.
-e <dd/mm/yyyy>: Optional. UTC end date. If not specified the current date is used.
-l <float>,<float>: Optional (default: 54.0085726,-1.4301757). Centroid, with a radius of 381 km
-r <int>: Optional (default: 381 km)
-t: Optional. Print transit days
-v: Optional. Verbose

Build jar:

Build menu > Build Artifacts > Build