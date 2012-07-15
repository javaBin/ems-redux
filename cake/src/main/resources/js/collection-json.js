
function toObject() {
  return _.reduce(this.data, function(map, field) {
    map[field.name] = field.value;
    return map;
  }, {});
}

function toObject(withData) {
  return _.reduce(withData.data, function(map, field) {
    map[field.name] = field.value;
    return map;
  }, {});
}

function toItems(data) {
    return data.collection.items.map(function(i) {
        var obj = {};
        obj.href = i.href;
        obj.data = toObject(i);
        return obj;
    });
}


function fixTemplate(t) {
  t.toObject = toObject;
  t.data = _.isArray(t.data) ? t.data : [];
}

/**
 * Takes any object and make it look like a collection+json collection object.
 */
function fromObject(root) {
//  console.log('in ', root);

  root.isCollection = function() {
    return typeof this.collection == 'object';
  }
  root.isTemplate = function() {
    return typeof this.template == 'object';
  }

  if(root.isTemplate()) {
    fixTemplate(root.template);
  }
  else {
    root = _.isObject(root) ? root : {collection: {}};
    if(!_.isObject(root.collection)) {
      root.collection = {};
    }
    var c = root.collection;

    c.version = _.isString(c.version) ? c.version : "1.0";
    c.items = _.isArray(c.items) ? c.items : [];
    _.each(c.items, function(item) {
      item.links = _.isArray(item.links) ? item.links : [];
      item.toObject = toObject;
    });

    c.links = _.isArray(c.links) ? c.links : [];

    c.queries = _.isArray(c.queries) ? c.queries : [];
    _.each(c.queries, function(query) {
      query.data = _.isArray(query.data) ? query.data : [];
    });

    if(_.isObject(c.template)) {
      fixTemplate(c.template);
    }
    else {
      delete c.template;
    }
  }

  // c.templates = _.isArray(c.templates) ? c.templates : [];

  // TODO: make un-enumerable
  root.mapItems = function(f) {
    return _.map(this.collection.items, function(item) {
      return f(item.toObject());
    });
  };
  root.findLinkByRel = function(rel) {
    return _.find(this.collection.links, function(link) {
      return rel === link.rel;
    });
  }

//  console.log('out', root);
  return root;
}
//module.exports.fromObject = fromObject;

function fromString(s) {
  var object = {};
  try {
    object = JSON.parse(s);
  }
  catch(ex) {
  }
  return fromObject(object);
}
//module.exports.fromString = fromString;
