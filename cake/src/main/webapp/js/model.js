
function EmsEvent(i) {
  var obj = i.toObject();
  return {
    item: i,
    object: obj,
    sessions: i.findLinkByRel("session collection").href
  }
}

function EmsSlot(i) {
  var obj = i.toObject();
  var start = moment(obj.start);
  var end = moment(obj.end);
  var time = start.format("HH:mm") + "-" + end.format("HH:mm");
  return {
    item: i,
    object: obj,
    start: start,
    end: end,
    day: start.format("YYYY-MM-DD"),
    dayOfYear: start.dayOfYear(),
    duration: end.diff(start) / 1000 / 60,
    dayFormatted: start.format("YYYY-MM-DD") + ": " + time,
    formatted: time
  }
}

function EmsRoom(i) {
  var obj = i.toObject();
  return {
    item: i,
    object: obj
  }
}

function EmsSession(i) {
  var obj = i.toObject();
  var spk = i.findLinksByRel("speaker item");
  return {
    item: i,
    object: obj,
    keywordsAsString: toCSV(obj.keywords),
    tagsAsString: toCSV(obj.tags),
    state: sessionHelpers.mapState(obj.state),
    format: sessionHelpers.mapFormat(obj.format),
    level: sessionHelpers.mapLevel(obj.level),
    lang: sessionHelpers.mapLang(obj.lang),
    body_html: wiki.parse(obj.body),
    outline_html: wiki.parse(obj.outline),
    audience_html: wiki.parse(obj.audience),
    summary_html: wiki.parse(obj.summary),
    equipment_html: wiki.parse(obj.equipment),
    room: i.findLinkByRel("room item"),
    slot: sessionHelpers.handleSlot(i.findLinkByRel("slot item")),
    speakers: spk,
    speakersAsString: toCSV(_.map(spk, function (s) {
      return s.prompt
    }))
  }
}

function EmsSpeaker(i) {
  var obj = i.toObject();
  var result = {
    item: i,
    object: obj
  };
  var photo = i.findLinkByRel("photo");
  if (photo && ("image" === photo.render)) {
    result.photo = photo.href;
  }

  return result;
}

function toCSV(list) {
  return _.reduce(list, function (agg, i) {
    var out = agg;
    if (agg.length > 0) {
      out = agg + ", "
    }
    return out + i;
  }, "");
}