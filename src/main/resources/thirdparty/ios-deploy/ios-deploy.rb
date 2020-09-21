class IosDeploy < Formula
  desc "Install and debug iPhone apps from the command-line"
  homepage "https://github.com/ios-control/ios-deploy"
  url "https://github.com/ios-control/ios-deploy/archive/1.11.2.tar.gz"
  sha256 "bca53cfb2a189e95cc8714d859a8821c3e6d5b9938ac74a7b3b69ea037e38244"
  license all_of: ["GPL-3.0-or-later", "BSD-3-Clause"]
  head "https://github.com/ios-control/ios-deploy.git"

  bottle do
    cellar :any_skip_relocation
    sha256 "2a08ef2f7fe4437a71b1ccfbe22e952fe6faa4850846417d09cb71d915f7b85d" => :catalina
    sha256 "b90dde5c2b82daca41855e53d71fc3c21d29f06e3abbbc71de93ac9e6e961e5d" => :mojave
    sha256 "93e988bd10d8b4419077ecbc9370569017c0a5241eeff8e89db7f8f47d530628" => :high_sierra
  end

  depends_on xcode: :build

  patch do
     url "PATCH_PATH"
     sha256 "c74520be482879bbef54fc775c8966cb2633847b5a489ecbd745c1eaaad1b7da"
  end

  def install
    xcodebuild "-configuration", "Release", "SYMROOT=build"

    xcodebuild "test", "-scheme", "ios-deploy-tests", "-configuration", "Release", "SYMROOT=build"

    bin.install "build/Release/ios-deploy"
  end

  test do
    system "#{bin}/ios-deploy", "-V"
  end
end