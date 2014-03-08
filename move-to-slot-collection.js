
conn = new Mongo();
db = conn.getDB("ems");

function cont(a, obj) {
  var i = a.length;
  while (i--) {
    if (a[i] === obj) {
      return true;
    }
  }
  return false;
}

function arrGroupBy(a, f) {
  var obj = {};
  a.forEach(function(i) {
    var group = f(i);
    var arr = obj[group] || [];
    arr.push(i);
    obj[group] = arr;
  })
  return obj;
}


var allEvents = [];

var allSlots = {};

var allUsedSlots = {};

var events = db.event.find();
while(events.hasNext()) {
  var slots = [];
  var event = events.next();
  allEvents.push(event._id);
  event.slots.forEach(function(s) {
    var start = s.start.getTime();
    var end = s.end.getTime();
    slots.push({
      _id: s._id,
      start: s.start,
      eventId: event._id,
      duration: ( end - start) / 1000 / 60
    });
  });
  allSlots[event._id] = slots;

  var usedSlots = [];
  var sCursor = db.session.find({eventId: event._id, state: "approved"}, {slotId: 1});
  while(sCursor.hasNext()) {
    var slot = sCursor.next().slotId;
    if (slot != null) {
      usedSlots.push(slot);
    }
  }
  allUsedSlots[event._id] = usedSlots;
}

var slots = [];

allEvents.forEach(function(e) {
  var used = allUsedSlots[e];
  var availableSlots = allSlots[e];
  var filtered = availableSlots.filter(function(s){
    return cont(used, s._id);
  });
  var group = arrGroupBy(filtered, function(s){
    return s.duration;
  });
  var lightning = group[10];
  var talks = group[60] || [];
  talks.forEach(function(s) {
    var start = s.start.getTime();
    var stop = start + (s.duration * 60 * 1000);
    lightning.forEach(function(s2) {
      var start2 = s2.start.getTime()
      var stop2 = start2 + (s2.duration * 60 * 1000);
      if (start <= start2 && stop >= stop2) {
        s2.parentId = s._id;
        slots.push(s2);
      }
    });

    slots.push(s);
  });
});


slots.forEach(function(s) {
  db.slot.insert(s);
})