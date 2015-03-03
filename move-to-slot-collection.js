
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
    if (isString(s.end)) {
      s.end = ISODate(s.end);
    }
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

var filteredSlots = [];

allEvents.forEach(function(e) {
  var slots2 = [];
  var used = allUsedSlots[e];
  var availableSlots = allSlots[e];
  var filtered = availableSlots.filter(function(s){
    return cont(used, s._id);
  });
  if (filtered.length > 0) {
     slots2.push(filtered);
     filtered.forEach(function(e){
        filteredSlots.push(e);
     });
  }
});

print("Inserting " + filteredSlots.length + " slots");

filteredSlots.forEach(function(s) {
  db.slot.insert(s);
});

