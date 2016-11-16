#!/bin/python

"""
Usage:
    python create_net.py < input_file > output_file

Writes out a cleaned version of the Wikipedia file in the form:
TITLE: [article title]
SUMMARY: [summary]
REDIRECT: ['~' delimited list of redirects]
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
        while tag in self.tags:
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

def get_summary(text):
    parser = SummaryParser()
    for wiki_mark, tag in WIKI_MARKS_TO_HTML_TAG:
        text = text.replace(wiki_mark, tag)
    for line in text.split('\n'):
        parser.feed(line)
        if parser.data:
            if sum([len(d) for d in parser.data]) < 100:
                parser.data.append(' ')
                continue
            # Wikitext prefixes intro lines with ':', e.g. ': blah redirects here'; ignore.
            if parser.data[0][0] == ':':
                del parser.data[:]
                continue
            break
    return ''.join(parser.data)

def is_redirect_page(text):
    text = text.lower()
    return text[:len('#redirect')] == '#redirect' or '{{disambiguation' in text

def get_redirects(text):
    index = -1
    while True:
        index = text.find('[[', index + 1)
        if index == -1:
            break
        yield text[index + len('[[') : text.find(']]', index)].split('|')[0]

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
            if is_redirect_page(data):
                self.out.write('REDIRECT: %s\n' % '~'.join(get_redirects(data)))
            else:
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
        line = sub(r'[^\x00-\x7F]+', ' ', line)
        # Unmatched '&quot;'s mess up the HTMLParser; just remove them
        line = line.replace('&quot;', '')
        parser.feed(line)

