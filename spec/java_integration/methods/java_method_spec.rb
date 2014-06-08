require File.dirname(__FILE__) + "/../spec_helper"

describe "A Java object's java_method method" do
  before :each do
    @list = java.util.ArrayList.new
    @integer = java.lang.Integer.new(1)
  end
  
  it "works with name only for no-arg methods" do
    m = @list.java_method :toString
    m.call.should == "[]"
  end

  it "works with name plus empty array for no-arg methods" do
    m = @list.java_method :toString, []
    m.call.should == "[]"
  end
  
  it "works with a signature" do
    m = @list.java_method :add, [Java::int, java.lang.Object]
    m.call(0, 'foo')
    @list.to_s.should == "[foo]"
  end
  
  it "produces Method for static methods against an instance" do
    m = @integer.java_method :valueOf, [Java::int]
    # JRUBY-4107
    #m.call(1).should == @integer
    m.call(1).should == 1
  end
  
  it "produces UnboundMethod for instance methods against a class" do
    m = java.util.ArrayList.java_method :add, [Java::int, java.lang.Object]
    m.bind(@list).call(0, 'foo')
    @list.to_s.should == "[foo]"
  end
  
  it "produces Method for static methods against a class" do
    m = java.lang.Integer.java_method :valueOf, [Java::int]
    # JRUBY-4107
    #m.call(1).should == @integer
    m.call(1).should == 1
  end

  it "raises NameError if the method can't be found" do
    lambda do
      @list.java_method :foobar
    end.should raise_error(NameError)

    lambda do
      @list.java_method :add, [Java::long, java.lang.Object]
    end.should raise_error(NameError)
    
    lambda do
      java.lang.Integer.java_method :foobar
    end.should raise_error(NameError)
    
    lambda do
      java.lang.Integer.java_method :valueOf, [Java::long]
    end.should raise_error(NameError)
  end

  it "calls static methods" do
    lambda {
      import 'java_integration.fixtures.PackageStaticMethod'

      method = PackageStaticMethod.java_class.declared_method_smart :thePackageScopeMethod
      method.accessible = true
      method.invoke nil.to_java
    }.should_not raise_error
  end
end
