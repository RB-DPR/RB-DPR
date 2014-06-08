require File.expand_path('../../../spec_helper', __FILE__)
require File.expand_path('../fixtures/classes', __FILE__)
require File.expand_path('../shared/quo', __FILE__)

describe "Numeric#quo" do
  ruby_version_is ""..."1.9" do
    it_behaves_like :numeric_quo_18, :quo
  end

  ruby_version_is "1.9" do
    it "returns the result of self divided by the given Integer as a Rational" do
      5.quo(2).should eql(Rational(5,2))
    end

    it "returns the result of self divided by the given Float as a Float" do
      2.quo(2.5).should eql(0.8)
    end

    it "returns the result of self divided by the given Bignum as a Float" do
      45.quo(bignum_value).should be_close(1.04773789668636e-08, TOLERANCE)
    end

    it "raises a ZeroDivisionError when the given Integer is 0" do
      lambda { 0.quo(0) }.should raise_error(ZeroDivisionError)
      lambda { 10.quo(0) }.should raise_error(ZeroDivisionError)
      lambda { -10.quo(0) }.should raise_error(ZeroDivisionError)
      lambda { bignum_value.quo(0) }.should raise_error(ZeroDivisionError)
      lambda { -bignum_value.quo(0) }.should raise_error(ZeroDivisionError)
    end

    it "returns the result of calling self#/ with other" do
      obj = NumericSpecs::Subclass.new
      obj.should_receive(:coerce).twice.and_return([19,19])
      obj.should_receive(:<=>).any_number_of_times.and_return(1)
      obj.should_receive(:/).and_return(20)

      obj.quo(19).should == 20
    end

    it "raises a TypeError when given a non-Integer" do
      lambda {
        (obj = mock('x')).should_not_receive(:to_int)
        13.quo(obj)
      }.should raise_error(TypeError)
      lambda { 13.quo("10")    }.should raise_error(TypeError)
      lambda { 13.quo(:symbol) }.should raise_error(TypeError)
    end
  end
end
