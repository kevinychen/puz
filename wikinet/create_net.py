#!/bin/python

"""
Usage:
    python create_net.py < input_file > output_file

Writes out a cleaned version of the Wikipedia file in the form:
TITLE: [article title]
SUMMARY: [summary]
where [summary] is one of:
    [first line of main article]
    #REDIRECT [redirect link]
    #REFERENCE [pipe delimited list of reference links]

TODO:
    - Pages that start with '[blah] redirects here'
    - Pages that start with '#redirect' or '#REDIRECT[blah]' without a delimiting space
    - Pages with an unclosed quote in an HTML tag <ref name='[blah] [no ending quote]...>
    - Other reference keywords, e.g. 'a list of', and 'List of'
"""

from HTMLParser import HTMLParser
from htmlentitydefs import name2codepoint
from re import sub
from sys import stdin, stdout

TAGS_TO_IGNORE_WIKI_MARKS = ['math', 'math chem', 'ce']
TAGS_TO_CLEAN = ['curly', 'pipe', 'ref', 'math', 'div']
WIKI_MARKS_TO_HTML_TAG = [('{{', '<curly>'), ('}}', '</curly>'),
        ('[[', '<square>'), (']]', '</square>'),
        ('{|', '<pipe>'), ('|}', '</pipe>')]
REFERENCE_KEYWORDS = ['may refer to:', '==Events==']

class SummaryParser(HTMLParser):
    def __init__(self):
        HTMLParser.__init__(self)
        self.tags = []
        self.data = []
    def handle_starttag(self, tag, attrs):
        if any([t in self.tags for t in TAGS_TO_IGNORE_WIKI_MARKS]):
            return
        # HTMLParser incorrectly parses <ref name=blah/> as <ref name="blah/"> instead of <ref name="blah" />
        if attrs and attrs[-1][1] and attrs[-1][1][-1] == '/':
            return
        self.tags.append(tag)
    def handle_endtag(self, tag):
        if any([t in self.tags for t in TAGS_TO_IGNORE_WIKI_MARKS]) and self.tags[-1] != tag:
            return
        while self.tags:
            if self.tags.pop() == tag:
                break
    def handle_data(self, data):
        if data.isspace():
            return
        if any([t in self.tags for t in TAGS_TO_CLEAN]):
            return
        # Text in square brackets [[ ... ]] before the summary text should be ignored.
        if 'square' in self.tags and not self.data:
            return
        self.data.append(data)

def get_references(text):
    index = -1
    while True:
        index = text.find('<square>', index + 1)
        if index == -1:
            break
        yield text[index + len('<square>') : text.find('</square>', index)].split('|')[0]

def get_summary(text):
    parser = SummaryParser()
    for wiki_mark, tag in WIKI_MARKS_TO_HTML_TAG:
        text = text.replace(wiki_mark, tag)
    for line in text.split('\n'):
        parser.feed(line)
        if parser.data:
            break
    summary = ''.join(parser.data)
    if any([k in summary for k in REFERENCE_KEYWORDS]):
        summary = '#REFERENCE ' + '|'.join(get_references(text))
    return summary

class WikiParser(HTMLParser):
    def __init__(self, out):
        HTMLParser.__init__(self)
        self.out = out
    def handle_starttag(self, tag, attrs):
        self.data = []
    def handle_endtag(self, tag):
        data = ''.join(self.data)
        if tag == 'title':
            self.title = data
        elif tag == 'text' and self.title:
            self.out.write('TITLE: %s\n' % self.title)
            self.out.write('SUMMARY: %s\n' % get_summary(data))
    def handle_data(self, data):
        self.data.append(data)
    def handle_entityref(self, name):
        self.data.append(chr(name2codepoint[name]))

if __name__ == '__main__':
    parser = WikiParser(stdout)
    index = 0
    for line in stdin.readlines():
        # Remove all non-ASCII characters because they sometimes trip up HTMLParser
        parser.feed(sub(r'[^\x00-\x7F]+', ' ', line))

