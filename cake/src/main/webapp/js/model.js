var DT = {}
DT.parse = function(dateString) {
  return moment.utc(dateString, 'yyyy-MM-ddTHH:mm:ssZ').toDate()
}
DT.format = function(dt) {
  return moment.utc(dt).format('yyyy-MM-ddTHH:mm:ssZ');
}

function EmsEvent(i) {
  var obj = i.toObject();
  return {
    item: i,
    object: obj,
    start: DT.parse(obj.start),
    end: DT.parse(obj.end),
    sessions: i.findLinkByRel("session collection").href
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