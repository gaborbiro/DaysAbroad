java -jar DaysAbroad.jar -s "Location History.json"

Some optional arguments:
java -jar DaysAbroad.jar -s "Location History.json" -t 1825 -c 54.0085726,-1.4301757 -r 381

-s <file path>: . Days spent outside of UK. File path must point to google location history takeout file in json format. (See https://takeout.google.com/settings/takeout)
-t <int>: Optional (default: 5 years or 1825 days). Number of days in the past to analyse
-l <float>,<float>: Optional (default: 54.0085726,-1.4301757). Centroid, with a radius of 381 km
-r <int>: Optional (default: 381 km)
-d: Print debug information