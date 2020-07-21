#!/usr/bin/env ruby
require 'time'

class AppVersion
  def self.version
    dir=File.expand_path File.dirname(__FILE__)
    version_file="#{dir}/version.txt"
    last_commit = Time.parse(`git log -1 --date=iso --format=%cd`)
    last_version=Time.parse(`git log -1 --date=iso --format=%cd #{version_file}`)
    version = '1.1'
    version = `cat #{version_file}`.strip if ENV['TRAVIS_BRANCH'] == 'master'
    version = "3.#{ENV['TRAVIS_PULL_REQUEST']}" if ENV['TRAVIS_PULL_REQUEST'] != nil && ENV['TRAVIS_PULL_REQUEST'] != 'false'
    "#{version}.#{(last_commit - last_version).to_i/600}"
  end
end
puts AppVersion.version