// Google live traffic, unofficial: Google's own traffic tiles laid over the OpenStreetMap / OpenFreeMap /
// offline map. Separate from the official Google Maps API traffic (map.html setTraffic with a key).
// PERSONAL USE ONLY: not allowed by Google's terms. Switched on/off from Java by GoogleTraffic.ENABLED;
// remove this file and GoogleTraffic.java before publishing to the Play Store.
var GTraffic=(function(){
  // lyrs: 'h,traffic' = road labels + traffic lines on a transparent background
  var LYRS='h,traffic', REFRESH=180000; // fresh traffic every 3 minutes
  var allowed=false, want=false, lf=null, stamp=Date.now(), timer=null;
  function url(s){ return 'https://mt'+s+'.google.com/vt?lyrs='+LYRS+'&hl=en&x={x}&y={y}&z={z}&t='+stamp; }
  function glDark(){ var e=document.getElementById('m'); return e&&/\bdark\b/.test(e.className); }

  // Leaflet (Standard): its own pane above the tiles, so the night inversion doesn't recolour the traffic
  function onLeaflet(){
    if(!map.getPane('gtraffic')){ var p=map.createPane('gtraffic'); p.style.zIndex=250; p.style.pointerEvents='none'; }
    if(!lf) lf=L.tileLayer(url('{s}'),{subdomains:'0123',maxZoom:20,pane:'gtraffic',opacity:.9});
    if(!map.hasLayer(lf)) lf.addTo(map);
  }
  // MapLibre (OpenFreeMap / offline): a raster layer; pre-inverted at night because the dark map inverts the canvas
  function onGl(){
    if(!gl.isStyleLoaded()) return false;
    if(!gl.getSource('gtraffic')) gl.addSource('gtraffic',{type:'raster',tileSize:256,
      tiles:['0','1','2','3'].map(url)});
    if(!gl.getLayer('gtraffic')){
      var d=glDark();
      gl.addLayer({id:'gtraffic',type:'raster',source:'gtraffic',paint:{'raster-opacity':.9,'raster-fade-duration':0,
        'raster-brightness-min':d?1:0,'raster-brightness-max':d?0:1,'raster-hue-rotate':d?180:0}});
    }
    return true;
  }
  function off(){
    try{ if(lf&&map&&map.hasLayer(lf)) map.removeLayer(lf); }catch(e){}
    try{ if(gl&&gl.getLayer('gtraffic')) gl.removeLayer('gtraffic'); if(gl&&gl.getSource('gtraffic')) gl.removeSource('gtraffic'); }catch(e){}
  }
  // the map may not exist yet when this is called: keep trying until it's on
  function apply(){
    clearTimeout(timer);
    if(!allowed||!want){ off(); return; }
    var done=false;
    try{ if(map) { onLeaflet(); done=true; } else if(gl) done=onGl(); }catch(e){}
    timer=setTimeout(function(){
      if(done){ stamp=Date.now(); // swap in fresh tiles without removing the layer (no flicker)
        try{ if(lf) lf.setUrl(url('{s}')); var s=gl&&gl.getSource('gtraffic'); if(s&&s.setTiles) s.setTiles(['0','1','2','3'].map(url)); }catch(e){} }
      apply();
    }, done?REFRESH:1000);
  }
  return {
    allow:function(b){ allowed=!!b; apply(); },
    set:function(b){ want=!!b; apply(); }
  };
})();
