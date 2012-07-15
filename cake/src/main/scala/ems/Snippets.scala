package ems

import xml.NodeSeq

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object Snippets {

  def template(content: NodeSeq) = {
    <html lang="en">
      <head>
        <meta charset="utf-8"/>
        <title>The cake is a lie</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
        <meta name="description" content=" "/>
        <meta name="author" content=" "/>

        <!-- Le styles -->
        <link href="/bootstrap-2.0.4/css/bootstrap.css" rel="stylesheet"/>
        <style>
          {"body {padding-top: 60px;}"}
        </style>
        <link href="/bootstrap-2.0.4/css/bootstrap-responsive.css" rel="stylesheet"/>

        <link href={EmsConfig.root.toString} rel="nofollow ems"/>

        <!-- Le HTML5 shim, for IE6-8 support of HTML5 elements -->
        <!--[if lt IE 9]>
        <script src="http://html5shim.googlecode.com/svn/trunk/html5.js"></script>
        <![endif]-->

        <!-- Le fav and touch icons -->
        <link rel="shortcut icon" href="/img/favicon.ico"/>
      </head>
      <body>

        <div class="navbar navbar-fixed-top">
          <div class="navbar-inner">
            <div class="container">
              <a class="btn btn-navbar" data-toggle="collapse" data-target=".nav-collapse">
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
              </a>
              <a class="brand" href="#">Project name</a>
              <div class="nav-collapse">
                <ul class="nav">
                  <li class="active">
                    <a href="#">Home</a>
                  </li>
                  <li>
                    <a id="events" href="#">Events</a>
                  </li>
                  <li>
                    <a href="/contacts">Contacts</a>
                  </li>
                </ul>
              </div> <!--/.nav-collapse -->
            </div>
          </div>
        </div>

        <div id="mainContent" class="container">
          {content}
        </div> <!-- /container -->

        <!-- Le javascript -->
        <!-- Placed at the end of the document so the pages load faster -->
        <script src="/js/jquery-1.7.2.min.js"></script>
        <script src="/bootstrap-2.0.4/js/bootstrap.min.js"></script>
        <script src="/js/underscore.js"></script>
        <script src="/js/collection-json.js"></script>
        <script src="/js/mustache.js"></script>
        <script src="/js/cake.js"></script>

      </body>
    </html>

  }
}
