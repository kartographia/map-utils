package com.kartographia.map;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.text.DecimalFormat;
import java.math.BigDecimal;
import java.util.*;

//******************************************************************************
//**  MapTile
//******************************************************************************
/**
 *   Used to generate images that are rendered on a map. Can be used render
 *   points, lines, polygons, etc.
 *
 ******************************************************************************/

public class MapTile {

    private double ULx = 0;
    private double ULy = 0;
    private double resX = 1;
    private double resY = 1;

    protected javaxt.io.Image img;
    protected Graphics2D g2d;
    private ArrayList<Rectangle> textboxes = new ArrayList<>();

    private String wkt;
    private double north;
    private double south;
    private double east;
    private double west;
    private Geometry geom;
    private int srid;


    private static DecimalFormat df = new DecimalFormat("#.##");
    static{ df.setMaximumFractionDigits(8); }



    private static PrecisionModel precisionModel = new PrecisionModel();
    private static GeometryFactory geometryFactory = new GeometryFactory(precisionModel, 4326);


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public MapTile(double minX, double minY, double maxX, double maxY, int width, int height, int srid){
        init(minX, minY, maxX, maxY, width, height, srid);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public MapTile(int x, int y, int z, int size){
        double north = tile2lat(y, z);
        double south = tile2lat(y + 1, z);
        double west = tile2lon(x, z);
        double east = tile2lon(x + 1, z);
        int srid = 3857;
        double[] sw = get3857(south, west);
        double[] ne = get3857(north, east);
        init(sw[0], sw[1], ne[0], ne[1], size, size, srid);
    }


  //**************************************************************************
  //** init
  //**************************************************************************
    private void init(double minX, double minY, double maxX, double maxY,
        int width, int height, int srid){



        this.srid = srid;
        if (srid==3857){


          //Convert coordinates to lat/lon
            north = getLat(maxY);
            south = getLat(minY);
            east = getLon(maxX);
            west = getLon(minX);


          //Validate Coordinates
            if (!valid(west, south, east, north)) throw new IllegalArgumentException();


          //Set wkt
            String NE = df.format(east) + " " + df.format(north);
            String SE = df.format(east) + " " + df.format(south);
            String SW = df.format(west) + " " + df.format(south);
            String NW = df.format(west) + " " + df.format(north);
            wkt = "POLYGON((" + NE + "," +  NW + "," + SW + "," + SE + "," + NE + "))";



            ULx = minX;
            ULy = maxY;


          //Compute pixelsPerMeter
            resX = width  / diff(minX,maxX);
            resY = height / diff(minY,maxY);

        }
        else if (srid==4326){


          //Validate Coordinates
            if (!valid(minX, minY, maxX, maxY)) throw new IllegalArgumentException();



          //Get wkt
            String NE = df.format(maxX) + " " + df.format(maxY);
            String SE = df.format(maxX) + " " + df.format(minY);
            String SW = df.format(minX) + " " + df.format(minY);
            String NW = df.format(minX) + " " + df.format(maxY);
            wkt = "POLYGON((" + NE + "," +  NW + "," + SW + "," + SE + "," + NE + "))";
            north = maxY;
            south = minY;
            east = maxX;
            west = minX;





          //Update min/max coordinates
            minX = x(minX);
            minY = y(minY);
            maxX = x(maxX);
            maxY = y(maxY);


          //Update Local Variables using updated values
            ULx = minX;
            ULy = maxY;


          //Compute pixelsPerDeg
            resX = ((double) width)  / (maxX-minX);
            resY = ((double) height) / (minY-maxY);
        }
        else{
            throw new IllegalArgumentException("Unsupported projection");
        }


      //Create image
        img = new javaxt.io.Image(width, height);
        g2d = img.getBufferedImage().createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        //applyQualityRenderingHints(g2d);
        g2d.setColor(Color.BLACK);
    }


  //**************************************************************************
  //** clear
  //**************************************************************************
    public void clear(){
        g2d.setBackground(new Color(0, 0, 0, 0));
        g2d.clearRect(0, 0, img.getWidth(), img.getHeight());
        g2d.setColor(Color.BLACK);
    }


  //**************************************************************************
  //** getSRID
  //**************************************************************************
    public int getSRID(){
        return srid;
    }


  //**************************************************************************
  //** getBounds
  //**************************************************************************
  /** Returns the tile boundary as a Well-Known Text (WKT) in lat/lon
   *  coordinates (EPSG:4326)
   */
    public String getBounds(){
        return wkt;
    }


  //**************************************************************************
  //** getGeometry
  //**************************************************************************
  /** Returns the tile boundary as a lat/lon geometry (EPSG:4326)
   */
    public Geometry getGeometry(){
        if (geom==null){
            try{
                geom = new WKTReader().read(wkt);
            }
            catch(Exception e){
                //should never happen
            }
        }
        return geom;
    }


  //**************************************************************************
  //** getWidth
  //**************************************************************************
    public int getWidth(){
        return img.getWidth();
    }


  //**************************************************************************
  //** getHeight
  //**************************************************************************
    public int getHeight(){
        return img.getHeight();
    }


    public double getNorth(){
        return north;
    }

    public double getSouth(){
        return south;
    }

    public double getEast(){
        return east;
    }

    public double getWest(){
        return west;
    }


  //**************************************************************************
  //** getImage
  //**************************************************************************
    public javaxt.io.Image getImage(){
        return img;
    }


  //**************************************************************************
  //** isEmpty
  //**************************************************************************
  /** Returns true if this is a blank or empty tile (e.g. all pixels are
   *  transparent)
   */
    public boolean isEmpty(){
        for (int i=0; i<img.getWidth(); i++){
            for (int j=0; j<img.getHeight(); j++){
                Color color = img.getColor(i, j);
                int r = color.getRed();
                int g = color.getGreen();
                int b = color.getBlue();
                int a = color.getAlpha();
                if (a>0) return false;

            }
        }
        return true;
    }


  //**************************************************************************
  //** setBackgroundColor
  //**************************************************************************
    public void setBackgroundColor(int r, int g, int b){
        setBackgroundColor(new Color(r,g,b));
    }

    public void setBackgroundColor(Color color){
        Color org = g2d.getColor();
        g2d.setColor(color);
        g2d.fillRect(0,0,img.getWidth(),img.getHeight());
        g2d.setColor(org);
    }


  //**************************************************************************
  //** addText
  //**************************************************************************
  /** Used to add text to the image. Note that the text is automatically
   *  cropped to fit the map tile. Therefore, it is highly recommended to add
   *  text from neighboring tiles. Also note that text blocks cannot overlap
   *  or intersect one another. It is therefore recommended that text is added
   *  in an orderly and consistent fashion across all tiles. For example,
   *  consider sorting the text coordinates from east to west and north to
   *  south. Example:
   <pre>

      //Create an array of labels/text to add to the map tile
        ArrayList&lt;Object[]> labels = new ArrayList&lt;>();
        //labels.add(new Object[]{text, lat, lon});


      //Sort coordinates from nw to se
        double[][] points = new double[labels.size()][2];
        for (int i=0; i&lt;points.length; i++){
            Object[] data = labels.get(i);
            double lat = (double) data[1];
            double lon = (double) data[2];
            points[i] = new double[]{lon+180.0,lat+90.0};
        }
        Arrays.sort(points, new java.util.Comparator&lt;double[]>() {
            public int compare(double[] a, double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });


      //Render labels
        for (int i=0; i&lt;points.length; i++){
            double[] point = points[i];
            double lon = point[0]-180.0;
            double lat = point[1]-90.0;
            for (Object[] data : labels){
                if (isEqual(lat,(double) data[1]) && isEqual(lon,(double) data[2])){
                    String name = (String) data[0];
                    mapTile.addText(name, lat, lon, style);
                    break;
                }
            }
        }
   </pre>
   */
    public void addText(String text, double lat, double lon, MapStyle style){
        if (text==null) return;
        text = text.trim();



      //Get font style
        Font font = style.getFont();
        if (font==null) return;
        FontMetrics fm = g2d.getFontMetrics(font);
        Color fontColor = style.getColor();
        Color borderColor = style.getBorderColor();
        Float borderWidth = style.getBorderWidth();
        String align = style.getTextAlign();
        String valign = style.getTextVAlign();



      //Split text into lines as needed
        ArrayList<String> lines = new ArrayList<>();
        Integer textWrap = style.getTextWrap();
        String[] arr = textWrap==null ? null : text.split(" ");
        if (arr!=null && arr.length>1){
            int nIndex = 0;
            while ( nIndex < arr.length ){
                String line = arr[nIndex++];
                while ( ( nIndex < arr.length ) && (fm.stringWidth(line + " " + arr[nIndex]) < textWrap) ) {
                    line = line + " " + arr[nIndex];
                    nIndex++;
                }
                lines.add(line);
            }
        }
        else{
            lines.add(text);
        }



      //Compute bounting rectangles for each line
        double[] xy = getXY(lat, lon);
        ArrayList<Rectangle> rectangles = new ArrayList<>();
        int y = cint(xy[1]);
        for (int i=0; i<lines.size(); i++){
            String line = lines.get(i);
            int width = fm.stringWidth(line);
            int height = fm.getHeight();
            int descent = fm.getDescent();
            height = height-descent;


            int xOffset;
            double x = xy[0];
            if (align.equals("center")){
                xOffset = cint(x-(width/2.0));
            }
            else if (align.equals("right")){
                xOffset = cint(x-width);
            }
            else{
                xOffset = cint(x);
            }



            int yOffset;
            if (valign.equals("top")){
                yOffset = y - ((i+1)*height);
            }
            else if (valign.equals("bottom")){
                yOffset = y + ((i+1)*height);
            }
            else { //middle
                int totalHeight = height*lines.size();
                int yStart = y-cint(totalHeight/2.0);
                yOffset = yStart + (height*(i+1));
            }


            Rectangle rect = new Rectangle(xOffset, yOffset, width, height);
            for (Rectangle r : textboxes){
                if (r.intersects(rect)) return;
            }
            rectangles.add(rect);
        }




      //Draw text
        for (int i=0; i<lines.size(); i++){
            String line = lines.get(i);
            Rectangle rect = rectangles.get(i);

            if (borderWidth!=null){
                BasicStroke stroke = new BasicStroke(borderWidth);
                float offset = 0f; //borderWidth/2f;
                Shape textShape = font.createGlyphVector(g2d.getFontRenderContext(), line).getOutline(rect.x-offset, rect.y-offset);
                g2d.setColor(borderColor);
                g2d.setStroke(stroke);
                g2d.draw(textShape); // draw outline

                g2d.setColor(fontColor);
                g2d.fill(textShape); // fill the shape
            }
            else{
                g2d.setColor(fontColor);
                g2d.drawString(line, rect.x, rect.y);
            }


            textboxes.add(rect);
        }

    }


  //**************************************************************************
  //** addPixel
  //**************************************************************************
  /** Used to add a pixel to the image
   */
    public void addPixel(double lat, double lon, Color color){

      //Get center point
        double[] xy = getXY(lat, lon);
        double x = xy[0];
        double y = xy[1];

        g2d.setColor(color);
        g2d.fillRect(cint(x), cint(y), 1, 1);
        g2d.setColor(Color.BLACK);
    }


  //**************************************************************************
  //** addPoint
  //**************************************************************************
  /** Used to add a point to the image
   */
    public void addPoint(double lat, double lon, Color color, double size){

      //Get center point
        double[] xy = getXY(lat, lon);
        double x = xy[0];
        double y = xy[1];


      //Get upper left coordinate
        double r = size/2d;
        x = x-r;
        y = y-r;


      //Render circle
        g2d.setColor(color);
        g2d.fillOval(cint(x), cint(y), cint(size), cint(size));
        g2d.setColor(Color.BLACK);
    }


  //**************************************************************************
  //** addLine
  //**************************************************************************
  /** Used to add a polygon to the image
   */
    public void addLine(LineString lineString, Color lineColor, Stroke lineStyle){

        Object[] obj = getCoordinates(lineString);
        int[] xPoints = (int[]) obj[0];
        int[] yPoints = (int[]) obj[1];
        int numCoordinates = xPoints.length;

        Stroke org = g2d.getStroke();
        if (lineStyle!=null) g2d.setStroke(lineStyle);
        if (lineColor==null) lineColor = Color.black;
        g2d.setColor(lineColor);
        g2d.drawPolyline(xPoints, yPoints, numCoordinates);
        g2d.setStroke(org);
    }

    public void addLine(LineString lineString, Color lineColor){
        addLine(lineString, lineColor, null);
    }


  //**************************************************************************
  //** addPolygon
  //**************************************************************************
  /** Used to add a polygon to the image
   */
    public void addPolygon(Polygon polygon, Color lineColor, Stroke lineStyle, Color fillColor){


      //Get outer coordinates of the polygon
        Object[] obj;
        if (polygon.getNumInteriorRing()==0){
            obj = getCoordinates(polygon);
        }
        else{
            obj = getCoordinates(polygon.getExteriorRing());
        }
        int[] xPoints = (int[]) obj[0];
        int[] yPoints = (int[]) obj[1];
        int numCoordinates = xPoints.length;



      //Save outer coordinates into a list of rings
        ArrayList<Object[]> rings = null;
        if (lineColor!=null){
            rings = new ArrayList<>();
            rings.add(obj);
        }



      //Create area
        java.awt.geom.Area area = new java.awt.geom.Area(new java.awt.Polygon(xPoints, yPoints, numCoordinates));


      //Remove holes
        for (int i=0; i<polygon.getNumInteriorRing(); i++){
            obj = getCoordinates(polygon.getInteriorRingN(i));
            if (rings!=null) rings.add(obj);
            xPoints = (int[]) obj[0];
            yPoints = (int[]) obj[1];
            numCoordinates = xPoints.length;
            area.subtract(new java.awt.geom.Area(new java.awt.Polygon(xPoints, yPoints, numCoordinates)));
        }



      //Fill area
        if (fillColor!=null){
            g2d.setColor(fillColor);
            g2d.fill(area);
        }


      //Draw outer rings
        if (lineColor!=null){
            Stroke org = g2d.getStroke();
            if (lineStyle!=null) g2d.setStroke(lineStyle);
            g2d.setColor(lineColor);

            for (Object[] ring : rings){
                xPoints = (int[]) ring[0];
                yPoints = (int[]) ring[1];
                numCoordinates = xPoints.length;
                g2d.drawPolyline(xPoints, yPoints, numCoordinates);
            }

            g2d.setStroke(org);
        }
    }

    public void addPolygon(Polygon polygon, Color lineColor, Color fillColor){
        addPolygon(polygon, lineColor, null, fillColor);
    }


  //**************************************************************************
  //** getCoordinates
  //**************************************************************************
    private Object[] getCoordinates(Geometry geom){
        Coordinate[] coordinates = geom.getCoordinates();
        int[] xPoints = new int[coordinates.length];
        int[] yPoints = new int[coordinates.length];

        for (int i=0; i<coordinates.length; i++){
            Coordinate coordinate = coordinates[i];
            //xPoints[i] = cint(x(coordinate.x));
            //yPoints[i] = cint(y(coordinate.y));

            double x;
            double y;
            if (srid == 3857){
                x = x(getX(coordinate.x));
                y = y(getY(coordinate.y));
            }
            else if (srid == 4326){
                x = x(coordinate.x);
                y = y(coordinate.y);
            }
            else{
                throw new IllegalArgumentException("Unsupported projection");
            }


            xPoints[i] = cint(x);
            yPoints[i] = cint(y);
        }
        return new Object[]{xPoints, yPoints};
    }


  //**************************************************************************
  //** intersects
  //**************************************************************************
  /** Returns true if the tile intersects the given geometry.
   */
    public boolean intersects(String wkt) throws Exception {
        return intersects(new WKTReader().read(wkt));
    }


  //**************************************************************************
  //** intersects
  //**************************************************************************
  /** Returns true if the tile intersects the given geometry.
   */
    public boolean intersects(Geometry geom) throws Exception {
        return getGeometry().intersects(geom);
    }


  //**************************************************************************
  //** validate
  //**************************************************************************
  /** Used to validate coordinates used to invoke this class
   */
    private boolean valid(double minX, double minY, double maxX, double maxY){
        if (minX > maxX || minY > maxY) return false;
        if (minX < -180 || maxX < -180 || maxX > 180 || minX > 180) return false;
        if (minY < -90 || maxY < -90 || maxY > 90 || minY > 90) return false;
        return true;
    }


  //**************************************************************************
  //** x
  //**************************************************************************
  /** Used to convert a geographic coordinate to a pixel coordinate
   *  @param pt Longitude value if the tile is in EPSG:4326. Otherwise,
   *  assumes value is in meters.
   */
    protected double x(double pt){
        if (srid == 3857){
            double x = pt;
            double d = diff(ULx,x);
            if (x<ULx) d = -d;
            return d * resX;
        }
        else if (srid == 4326){
            pt += 180;
            double x = (pt - ULx) * resX;
            //System.out.println("X = " + x);
            return x;
        }
        else{
            throw new IllegalArgumentException("Unsupported projection");
        }
    }


  //**************************************************************************
  //** y
  //**************************************************************************
  /** Used to convert a geographic coordinate to a pixel coordinate
   *  @param pt Latitude value if the tile is in EPSG:4326. Otherwise,
   *  assumes value is in meters.
   */
    protected double y(double pt){

        if (srid == 3857){
            double y = pt;
            double d = diff(ULy,y);
            if (y>ULy) d = -d;
            return  d * resY;
        }
        else if (srid == 4326){

            pt = -pt;
            if (pt<=0) pt = 90 + -pt;
            else pt = 90 - pt;

            pt = 180-pt;



            double y = (pt - ULy) * resY;

            if (cint(y)==0 || cint(y)==-0) y = 0;
            //else y = -y;


            return y;
        }
        else{
            throw new IllegalArgumentException("Unsupported projection");
        }
    }


  //**************************************************************************
  //** getXY
  //**************************************************************************
  /** Returns the x/y pixel coordinate for a given lat/lon coordinate
   */
    public double[] getXY(double lat, double lon){
        double x;
        double y;
        if (srid == 3857){
            x = x(getX(lon));
            y = y(getY(lat));
        }
        else if (srid == 4326){
            x = x(lon);
            y = y(lat);
        }
        else{
            throw new IllegalArgumentException("Unsupported projection");
        }
        return new double[]{x, y};
    }


  //**************************************************************************
  //** lon
  //**************************************************************************
  /** Converts an x pixel value into longitude
   */
    protected double lon(int x){
        return (ULx + ((double) x)/resX) - 180;
    }

  //**************************************************************************
  //** lat
  //**************************************************************************
  /** Converts a y pixel value into latitude
   */
    protected double lat(int y){
        return 90 - (ULy + ((double) y)/resY);
    }


    private static final double originShift = 2.0 * Math.PI * 6378137.0 / 2.0; //20037508.34

  //**************************************************************************
  //** getLat
  //**************************************************************************
  /** Converts y coordinate in EPSG:3857 to a latitude in EPSG:4326
   */
    public static double getLat(double y){
        //double lat = Math.log(Math.tan((90 + y) * Math.PI / 360.0)) / (Math.PI / 180.0);
        //return lat * originShift / 180.0;
        double lat = (y/originShift)*180.0;
        return 180.0 / Math.PI * (2 * Math.atan( Math.exp( lat * Math.PI / 180.0)) - Math.PI / 2.0);
    }



  //**************************************************************************
  //** getLon
  //**************************************************************************
  /** Converts x coordinate in EPSG:3857 to a longitude in EPSG:4326
   */
    public static double getLon(double x){
        //return x * originShift / 180.0;
        return (x/originShift)*180.0;
    }


  //**************************************************************************
  //** getX
  //**************************************************************************
  /** Converts longitude in EPSG:4326 to a x coordinate in EPSG:3857
   */
    public static double getX(double lon){
        //return (lon*180.0)/originShift;
        return lon * originShift / 180.0;
    }


  //**************************************************************************
  //** getY
  //**************************************************************************
  /** Converts latitude in EPSG:4326 to a y coordinate in EPSG:3857
   */
    public static double getY(double lat){
        //return Math.atan(Math.exp(lat * Math.PI / 20037508.34)) * 360 / Math.PI - 90;
        double y = Math.log( Math.tan((90 + lat) * Math.PI / 360.0 )) / (Math.PI / 180.0);
        return y * originShift / 180.0;
    }


  //**************************************************************************
  //** getIntersectingTiles
  //**************************************************************************
  /** Returns an array of x,y map tile coordinates that intersect a given
   *  geometry
   */
    public static ArrayList<int[]> getIntersectingTiles(Geometry geom, int z){
        ArrayList<int[]> tiles = new ArrayList<>();
        if (geom instanceof Point){
            Point point = (Point) geom;
            tiles.add(getTileCoordinate(point.getY(), point.getX(), z));
        }
        else{

            int[] arr = getTileExtents(geom, z);

            int minX = arr[0];
            int minY = arr[1];
            int maxX = arr[2];
            int maxY = arr[3];


            for (int y=minY; y<=maxY; y++){
                for (int x=minX; x<=maxX; x++){
                    Geometry g = getTileGeometry(x,y,z);
                    if (g.intersects(geom)){
                        tiles.add(new int[]{x,y});
                    }
                }
            }
        }
        return tiles;
    }


  //**************************************************************************
  //** getTileExtents
  //**************************************************************************
  /** Returns an array of integers representing the min/max tile coordinates
   *  for a given geometry (minX, minY, maxX, maxY)
   */
    public static int[] getTileExtents(Geometry geom, int z){
        Envelope box = geom.getEnvelopeInternal();
        int[] ul = getTileCoordinate(box.getMaxY(), box.getMinX(), z);
        int[] lr = getTileCoordinate(box.getMinY(), box.getMaxX(), z);

        int minX = ul[0];
        int minY = ul[1];
        int maxX = lr[0]+1;
        int maxY = lr[1]+1;

        return new int[]{minX, minY, maxX, maxY};
    }


  //**************************************************************************
  //** getTileGeometry
  //**************************************************************************
  /** Returns a JTS Geometry for a given tile
   */
    public static Geometry getTileGeometry(int x, int y, int z){
        double north = tile2lat(y, z);
        double south = tile2lat(y + 1, z);
        double west = tile2lon(x, z);
        double east = tile2lon(x + 1, z);


        Coordinate ne = new Coordinate(east, north);
        Coordinate se = new Coordinate(east, south);
        Coordinate sw = new Coordinate(west, south);
        Coordinate nw = new Coordinate(west, north);


        Coordinate[] coordinates = new Coordinate[]{
            sw, se, ne, nw, sw
        };

        return geometryFactory.createPolygon(coordinates);
    }


  //**************************************************************************
  //** tile2lon
  //**************************************************************************
  /** Returns the west tile coordinate
   */
    public static double tile2lon(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }


  //**************************************************************************
  //** tile2lat
  //**************************************************************************
  /** Returns the north tile coordinate
   */
    public static double tile2lat(int y, int z) {
        double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

  //**************************************************************************
  //** get3857
  //**************************************************************************
  /** Returns the EPSG:3857 coordinate for a given lat, lon
   */
    public static double[] get3857(double lat, double lng) {
        double x = lng * 20037508.34 / 180;
        double y = Math.log(Math.tan((90 + lat) * Math.PI / 360)) / (Math.PI / 180);
        y = y * 20037508.34 / 180;
        return new double[] { x, y };
    }


  //**************************************************************************
  //** getTileCoordinate
  //**************************************************************************
  /** Returns the x,y coordinate of a map tile at another zoom level
   */
    public static int[] getTileCoordinate(int x, int y, int z, int n){

        if (n==z){
            return new int[]{x, y};
        }
        else if (n<z){
            int d = z-n;
            double m = Math.pow(2, d);

            int x1 = (int) Math.floor(x/m);
            int y1 = (int) Math.floor(y/m);
            return new int[]{x1, y1};
        }
        else{
            int d = n-z;
            int m = (int) Math.pow(2, d);
            int x1 = x*m;
            int y1 = y*m;
            return new int[]{x1, y1};
        }
    }


  //**************************************************************************
  //** getTileCoordinate
  //**************************************************************************
  /** Returns the x,y coordinate of a map tile. Credit:
   *  https://github.com/chriswhong/map-tile-functions/blob/master/latLngToTileXY.js
   */
    public static int[] getTileCoordinate(double lat, double lng, int zoom){

        double latitude = clip(lat, -85.05112878, 85.05112878);
        double longitude = clip(lng, -180, 180);


        double x = (longitude + 180.0) / 360.0 * (1 << zoom);
        double y = (1.0 - Math.log(Math.tan(latitude * Math.PI / 180.0) + 1.0 / Math.cos(lat* Math.PI / 180)) / Math.PI) / 2.0 * (1 << zoom);

        int tilex  = trunc(x);
        int tiley  = trunc(y);

        return new int[]{tilex, tiley};
    }


    private static double clip(double n, double minValue, double maxValue){
        return Math.min(Math.max(n, minValue), maxValue);
    }


  //**************************************************************************
  //** trunc
  //**************************************************************************
  /** Emulates the Math.trunc() function in JavaScript. Returns the integer
   *  part of a floating-point number by removing the fractional digits. In
   *  other words, the function cuts off the dot and the digits to the right
   *  of it.
   */
    private static int trunc(double n){
        return new BigDecimal(n).toBigInteger().intValue();
    }


  //**************************************************************************
  //** cint
  //**************************************************************************
  /** Converts a double to an integer. Rounds the double to the nearest int.
   */
    private int cint(Double d){
        return (int)Math.round(d);
    }


  //**************************************************************************
  //** diff
  //**************************************************************************
  /** Returns the difference between to numbers
   */
    public static double diff(double a, double b){
        double x = a-b;
        if (x<0) x = -x;
        return x;
    }


  //**************************************************************************
  //** diff
  //**************************************************************************
  /** Returns the difference between to numbers
   */
    public static BigDecimal diff(BigDecimal a, BigDecimal b){
        BigDecimal x = a.subtract(b);
        if (x.compareTo(BigDecimal.ZERO) < 0){
            x = x.negate();
        }
        return x;
    }


  //**************************************************************************
  //** isEqual
  //**************************************************************************
  /** Returns true if the two numbers are equal within 6 decimal places
   */
    public static boolean isEqual(double a, double b){
        return (Math.abs(a - b) < .000001);
    }

}