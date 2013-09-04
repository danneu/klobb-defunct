# klobb

Klobb is a work-in-progress Jekyll-inspired blog engine.

Each post is contained within its own folder including any other assets related to the post.

## Usage

For now Klobb must be compiled into a `.jar`, placed within the root of a blog folder, and run:

    java -jar klobb.jar

## Goals

1. Assets are nested within each post's folder
2. Turn any Klobb-friendly directory structure into a website

### 1. Assets are nested in each post's folder

The goal here is to contain all of a post's assets within the post's folder. All in one place.

This includes images, CSS, scripts, and even nested pages for things like demos.

Example Jekyll directory structure:

    images/
    ├── coffee-shop1.gif
    ├── coffee-shop2.jpg
    └── coffee-shop3.png
    js/
    ├── demo1.js
    └── demo2.js
    css/
    ├── demo1.css
    └── demo2.css
    _posts/
    ├── 2013-08-12-first-post.md
    ├── 2013-08-13-cool-javascript-demos.md
    └── 2013-08-15-my-favorite-coffee-shops.md

Equivalent Klobb directory structure:

    posts/
    ├── 2013-08-12-first-post/
    │   └── content.md
    ├── 2013-08-13-cool-javascript-demos/
    │   ├── content.md
    │   └── demo/
    │       ├── index.html
    │       ├── js/
    │       │   ├── demo1.js
    │       │   └── demo2.js
    │       └── css/
    │           ├── demo1.css
    │           └── demo2.css
    └── 2013-08-15-my-favorite-coffee-shops/
        ├── content.md
        └── img
            ├── coffee-shop1.gif
            ├── coffee-shop2.jpg
            └── coffee-shop3.png
            
You can refer to the nested URLs from your Markdown by using relative paths.

For example, 

    └── 2013-08-15-my-favorite-coffee-shops/
        ├── content.md
        └── img
            ├── coffee-shop1.gif
            ├── coffee-shop2.jpg
            └── coffee-shop3.png
            
`content.md` can display its three images with this Markdown:

    ![Epoch Coffee](img/coffee-shop1.gif)
    ![Strange Brew](img/coffee-shop2.jpg)
    ![Bennu](img/coffee-shop3.png)

### 2. Turn any Klobb-friendly directory structure into a website

As long as you comply with Klobb's directory structure expectations, I want you to be able to run:

    klobb server 5000
    
And Klobb will serve your blog.

Here's a full sample blog structure:

    my-blog/
    ├── config.clj
    ├── index.mustache
    ├── layouts
    │   ├── default.mustache
    │   └── posts.mustache
    ├── pages
    │   └── about-me.md
    ├── posts
    │   ├── 2013-02-02-my-first-post
    │   │   ├── content.md
    │   │   └── img
    │   │       └── sunshine.png
    │   ├── 2013-03-11-favorite-coffee-shops
    │   │   └── content.md
    │   ├── 2013-04-24-clojurescript-tutorial
    │   │   ├── content.md
    │   │   └── demo
    │   │       ├── index.html
    │   │       └── demo.js
    └── public
        ├── css
        │   ├── base.css
        │   └── skeleton.css
        └── js
            └── application.js


## Misc

Implement stuff like:

    $ klobb new my-blog
    $ klobb server [port] (prod/dev)
    $ klobb admin <- offer an admin UI with post previews & CRUD 
    
Barebones blog:

    my-blog/
      config.clj
      index.mustache
      pages/
        about-me.mustache
      layouts/
        default.mustache
      posts/
        2013-02-31-my-first-post/
          content.md
          

- TODO: Specify available post/layout/page options:

Posts:

    {:title String                required
     :slug String                 required   /posts/:slug/
     :created-at "yyy-mm-dd"      optional - defaults to date in yyy-mm-dd-post-title
     :published? Boolean          optional - defaults to true
     :disqus-id String            optional - defaults to :slug (this opt is necessary for blog posts that have existing Disqus comments.)
     :comments? Boolean           optional - defaults to true
     :layout String (Layout name) optional - defaults to "post"

- TODO: Implement `config.clj`
