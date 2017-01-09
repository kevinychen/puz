#!/bin/python

import glob, os, sys

for i in xrange(100):
    os.makedirs('processed/redirects/%02d' % i)
    os.makedirs('processed/summaries/%02d' % i)

for f in glob.glob('data/net*'):
    print 'processing', f
    redirect_lines = [[] for i in xrange(10000)]
    summary_lines = [[] for i in xrange(10000)]
    for line in open(f).readlines():
        if line.startswith('TITLE: '):
            title = filter(lambda c: c.isalpha(), line[len('TITLE: '):]).upper() 
        elif line.startswith('SUMMARY: '):
            summary = line[len('SUMMARY: '):].strip()
            if title and summary:
                summary_lines[hash(title) % 10000].append('%s %s\n' % (title, summary))
        elif line.startswith('REDIRECT: '):
            redirects = line[len('REDIRECT: '):].strip()
            for redirect in redirects.split('~'):
                redirect = filter(lambda c: c.isalpha(), redirect).upper()
                if title and redirect:
                    redirect_lines[hash(title) % 10000].append('%s %s\n' % (title, redirect))
                    redirect_lines[hash(redirect) % 10000].append('%s %s\n' % (redirect, title))
    for i in xrange(10000):
        with open('processed/redirects/%02d/%02d' % (i / 100, i % 100), 'a') as fh:
            for line in redirect_lines[i]:
                fh.write(line)
        with open('processed/summaries/%02d/%02d' % (i / 100, i % 100), 'a') as fh:
            for line in summary_lines[i]:
                fh.write(line)

