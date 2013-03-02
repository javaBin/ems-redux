var wiki = wiki || {};

wiki.parse = (function () {
  var cursor = function (lines) {
    var index = 0;
    var size = lines.length;

    return {
      currentLine: null,
      hasMore: function () {
        return index < size;
      },
      advance: function () {
        if (this.hasMore()) {
          this.currentLine = lines[index].trim();
          index += 1;
        }
        return this.currentLine;
      },
      result: [],
      append: function(input) {
        this.result.push(input);
      }
    }
  }

  function parse(input) {
    if (!input) {
      return undefined;
    }
    var lines = _.str.lines(input);
    var c = cursor(lines);
    while (c.hasMore()) {
      var line = c.advance();
      if (_.str.startsWith(line, 'h1.')) {
        c.append(makeDiv('heading-1', line.substring(line.indexOf('h1.')).trim()));
      }
      else if (_.str.startsWith(line, 'h2.')) {
        c.append(makeDiv('heading-2', line.substring(line.indexOf('h2.')).trim()));
      }
      else if (_.str.startsWith(line, '*')) {
        parseUnorderedList(c, 0);
      }
      else if (!_.str.isBlank(line)) {
        c.append('<p>'+ line +'</p>')
      }
    }
    return makeDiv('wiki', c.result.join(''));
  }

  function parseUnorderedList(cursor, level) {
    var one = _.str.repeat('*', level + 1);
    var two = _.str.repeat('*', level + 2);
    var line = cursor.currentLine;
    cursor.append('<ul>')
    do {
      if (_.str.startsWith(line, two)) {
        parseUnorderedList(cursor, level + 1)
      }
      if (!_.str.startsWith(line, one)) {
        break;
      }
      cursor.append('<li>' + line.substring(level + 1).trim() + '</li>');
      line = cursor.advance();
    } while(cursor.hasMore())
    cursor.append('</ul>');
  }

  function makeDiv(clazz, content) {
    return '<div class="' + clazz + '">' + content + '</div>'
  }

  return parse;

}());
