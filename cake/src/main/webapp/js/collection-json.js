
function toObject() {
  return _.reduce(this.data, function(map, field) {
      if (_.isArray(field.array)) {
          map[field.name] = field.array;
      }
      else if (_.isObject(field.object)) {
          map[field.name] = field.object;
      }
      else {
          map[field.name] = field.value;
      }

    return map;
  }, {});
}

function findLinkByRel(obj, rel) {
    return _.find(obj.links, function(link) {
        return rel === link.rel;
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
      item.findLinkByRel = function(rel) {
          return findLinkByRel(item, rel);
      };
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
    return findLinkByRel(this.collection, rel);
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
