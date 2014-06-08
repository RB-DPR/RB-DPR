require File.expand_path('../../../spec_helper', __FILE__)
require File.expand_path('../fixtures/methods', __FILE__)

describe "Time#<=>" do
  it "returns 1 if the first argument is a point in time after the second argument" do
    (Time.now <=> Time.at(0)).should == 1
    (Time.at(0, 100) <=> Time.at(0, 0)).should == 1
    (Time.at(1202778512, 100) <=> Time.at(1202778512, 99)).should == 1
  end

  it "returns 0 if time is the same as other" do
    (Time.at(1202778513) <=> Time.at(1202778513)).should == 0
    (Time.at(100, 100) <=> Time.at(100, 100)).should == 0
  end

  it "returns -1 if the first argument is a point in time before the second argument" do
    (Time.at(0) <=> Time.now).should == -1
    (Time.at(0, 0) <=> Time.at(0, 100)).should == -1
    (Time.at(100, 100) <=> Time.at(101, 100)).should == -1
  end

  ruby_version_is "1.9" do
    it "returns 1 if the first argument is a fraction of a microsecond after the second argument" do
      (Time.at(100, Rational(1,1000)) <=> Time.at(100, 0)).should == 1
    end

    it "returns 0 if time is the same as other, including fractional microseconds" do
      (Time.at(100, Rational(1,1000)) <=> Time.at(100, Rational(1,1000))).should == 0
    end

    it "returns -1 if the first argument is a fraction of a microsecond before the second argument" do
      (Time.at(100, 0) <=> Time.at(100, Rational(1,1000))).should == -1
    end
  end

  describe "given a non-Time argument" do
    ruby_version_is ""..."1.9" do
      it "returns nil" do
        (Time.now <=> Object.new).should == nil
      end
    end

    ruby_version_is "1.9" do
      it "returns nil if argument <=> self returns nil" do
        t = Time.now
        obj = mock('time')
        obj.should_receive(:<=>).with(t).and_return(nil)
        (t <=> obj).should == nil
      end

      it "returns -1 if argument <=> self is greater than 0" do
        t = Time.now
        r = mock('r')
        r.should_receive(:>).with(0).and_return(true)
        obj = mock('time')
        obj.should_receive(:<=>).with(t).and_return(r)
        (t <=> obj).should == -1
      end

      it "returns 1 if argument <=> self is not greater than 0 and is less than 0" do
        t = Time.now
        r = mock('r')
        r.should_receive(:>).with(0).and_return(false)
        r.should_receive(:<).with(0).and_return(true)
        obj = mock('time')
        obj.should_receive(:<=>).with(t).and_return(r)
        (t <=> obj).should == 1
      end

      it "returns 0 if argument <=> self is neither greater than 0 nor less than 0" do
        t = Time.now
        r = mock('r')
        r.should_receive(:>).with(0).and_return(false)
        r.should_receive(:<).with(0).and_return(false)
        obj = mock('time')
        obj.should_receive(:<=>).with(t).and_return(r)
        (t <=> obj).should == 0
      end
    end
  end
end
